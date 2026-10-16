from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "_libaums")

usb = root / "libaums/src/main/java/me/jahnen/libaums/core/usb/UsbCommunication.kt"
s = usb.read_text()
old_timeout = "const val TRANSFER_TIMEOUT = 5000"
new_timeout = "const val TRANSFER_TIMEOUT = 8000"
if old_timeout not in s:
    raise SystemExit("TRANSFER_TIMEOUT marker not found")
usb.write_text(s.replace(old_timeout, new_timeout, 1))

scsi = root / "libaums/src/main/java/me/jahnen/libaums/core/driver/scsi/ScsiBlockDevice.kt"
s = scsi.read_text()
old_attempts = "private const val MAX_RECOVERY_ATTEMPTS = 5"
new_attempts = "private const val MAX_RECOVERY_ATTEMPTS = 2"
if old_attempts not in s:
    raise SystemExit("MAX_RECOVERY_ATTEMPTS marker not found")
s = s.replace(old_attempts, new_attempts, 1)

# A failed bulk transfer may advance the ByteBuffer position before the retry.
# libaums originally reuses that advanced position, so the next attempt can try
# to set limit(position + transferLength) past capacity. Example seen on device:
# newLimit > capacity: (524288 > 262144). Restore the original buffer window
# before every low-level retry.
old_loop = '''    private fun transferCommand(command: CommandBlockWrapper, inBuffer: ByteBuffer) {
        var lastException: Exception? = null
        for(i in 0..MAX_RECOVERY_ATTEMPTS) {
            try {
'''
new_loop = '''    private fun transferCommand(command: CommandBlockWrapper, inBuffer: ByteBuffer) {
        var lastException: Exception? = null
        val retryBufferPosition = inBuffer.position()
        val retryBufferLimit = inBuffer.limit()
        for(i in 0..MAX_RECOVERY_ATTEMPTS) {
            inBuffer.position(retryBufferPosition)
            inBuffer.limit(retryBufferLimit)
            try {
'''
if old_loop not in s:
    raise SystemExit("ScsiBlockDevice transferCommand loop marker not found")
s = s.replace(old_loop, new_loop, 1)

old_retry = '''            } catch (e: IOException) {
                // Retry
                Log.w(TAG, (e.message ?: "IOException") + ", retrying...")
                lastException = e
            }

            Thread.sleep(100)
'''
new_retry = '''            } catch (e: IOException) {
                Log.w(TAG, (e.message ?: "IOException") + ", resetting Bulk-Only transport and retrying...")
                lastException = e
                try {
                    bulkOnlyMassStorageReset()
                } catch (resetError: Exception) {
                    Log.w(TAG, "Bulk-Only recovery reset failed: " + (resetError.message ?: resetError.javaClass.simpleName))
                }
            }

            Thread.sleep(200)
'''
if old_retry not in s:
    raise SystemExit("ScsiBlockDevice retry block not found")
s = s.replace(old_retry, new_retry, 1)
scsi.write_text(s)

print("Applied v0.1.6 libaums recovery + ByteBuffer retry-state patch")
