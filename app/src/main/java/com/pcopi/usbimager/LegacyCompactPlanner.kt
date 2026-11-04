package com.pcopi.usbimager

/**
 * v0.2.15 READ-ONLY Smart Compact feasibility report.
 *
 * This is deliberately NOT a filesystem resizer, not a verified minimum
 * filesystem size and not an image writer. An EXT4 with metadata_csum, 64bit,
 * flex_bg and sparse_super needs a filesystem-aware offline shrink on a copy.
 *
 * No data on the source USB or destination USB is ever changed here.
 */
object LegacyCompactPlanner {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB
    private const val RESERVED_BYTES = 64L * MIB

    fun format(report: ExtRebuildInspector.Report): String {
        val out = StringBuilder("\n=== Smart Compact Backup V1 • Read-only ===\n")
        out.append("สถานะ: วิเคราะห์ความพร้อมเท่านั้น (ยังไม่สร้างไฟล์ IMG แบบย่อ)\n")
        out.append("ต้นฉบับ: READ-ONLY • การทดลองย่อจะทำบนสำเนาเท่านั้น\n")
        if (report.layout != "MBR" || report.sectorBytes != 512 ||
            report.partitions.size != 3 ||
            report.partitions.map { it.number } != listOf(1, 2, 3)) {
            out.append("BLOCKED: ยังไม่รองรับโครงสร้างนี้ (ต้องเป็น MBR/512B/3 partitions)\n")
            return out.toString()
        }

        val (p1, p2, p3) = report.partitions
        val formatOkay = p1.filesystem == "FAT32" &&
            p2.filesystem == "EXT2" && p3.filesystem == "EXT4" &&
            p1.startLba == 2048L &&
            p2.startLba == p1.endLba + 1L &&
            p3.startLba == p2.endLba + 1L &&
            p3.endLba > p3.startLba &&
            p3.endLba < report.usbBytes / report.sectorBytes

        if (!formatOkay) {
            out.append("BLOCKED: ตำแหน่งพาร์ทิชันหรือระบบไฟล์ไม่ตรงกับ Arc 3-partition\n")
            return out.toString()
        }

        val healthy = p2.cleanState == true && p3.cleanState == true &&
            p2.journalRecoveryRequired == false &&
            p3.journalRecoveryRequired == false
        if (!healthy) {
            out.append("BLOCKED: EXT2/EXT4 ไม่ Clean หรือมี Journal ค้าง\n")
            out.append("ห้ามซ่อมบน USB ต้นฉบับ ให้ตรวจบนสำเนาที่ปลอดภัยเท่านั้น\n")
            return out.toString()
        }

        val lastUsedPartitionByte = (p3.endLba + 1L) * report.sectorBytes
        val hasTail = lastUsedPartitionByte < report.usbBytes
        val allocatedBytes = p3.allocatedBytes
        val fsBytes = p3.filesystemBytes
        val blockBytes = p3.blockBytes?.toLong()

        out.append("ARC3: LBA ").append(p3.startLba).append("–").append(p3.endLba).append('\n')
        out.append("ขนาดข้อมูลจนสุด ARC3 ตามตาราง MBR: ")
            .append(FormatUtils.bytes(lastUsedPartitionByte)).append('\n')
        out.append("ARC3 filesystem: ")
            .append(fsBytes?.let(FormatUtils::bytes) ?: "ไม่ทราบ").append('\n')
        out.append("ARC3 allocated (รวม Metadata): ")
            .append(allocatedBytes?.let(FormatUtils::bytes) ?: "ไม่ทราบ").append('\n')
        out.append("ขอบเขต USB ต้นฉบับ: ").append(FormatUtils.bytes(report.usbBytes)).append('\n')

        if (hasTail && lastUsedPartitionByte <= 16L * GIB) {
            out.append("แนวทาง: มีพื้นที่ท้ายพาร์ทิชัน • ใช้ ARC Boot Compact แบบไม่ย่อได้ ")
                .append("หากผ่านการตรวจของเมนูสร้าง Image\n")
        } else {
            out.append("แนวทาง: ARC3 ขยายถึง/ใกล้ท้าย USB หรือใหญ่เกินโหมด Compact เดิม\n")
            out.append("ต้องใช้ e2fsck + resize2fs ที่รองรับ EXT4 แล้วปรับ MBR บนสำเนา ")
                .append("ก่อนจะสร้าง Image สำหรับ USB เล็ก\n")
        }

        val theoreticalUsed = allocatedBytes
        val startBytes = p3.startLba * report.sectorBytes
        val targets = if (report.targetBytes != null) {
            listOf("USB ปลายทางที่เลือก" to report.targetBytes)
        } else {
            // Decimal-capacity USB examples. Real devices may be smaller.
            listOf("USB 8 GB (ตัวอย่าง)" to 8_000_000_000L,
                   "USB 16 GB (ตัวอย่าง)" to 16_000_000_000L)
        }

        for ((name, capacity) in targets) {
            val space = capacity - startBytes - RESERVED_BYTES
            out.append(name).append(": ")
            when {
                capacity <= startBytes + RESERVED_BYTES -> out.append("พื้นที่ไม่เพียงพอ")
                theoreticalUsed == null || blockBytes == null ->
                    out.append("ยังประเมินพื้นที่ที่จัดสรรไม่ได้")
                theoreticalUsed >= space -> out.append("BLOCKED: เนื้อที่จัดสรรเกินงบพื้นที่")
                else -> out.append("พื้นที่เชิงปริมาณอาจพอ แต่ยังไม่ผ่านการย่อและตรวจ Metadata")
            }
            out.append(" • ARC3 สูงสุดหลังเผื่อท้าย: ")
                .append(FormatUtils.bytes(space.coerceAtLeast(0L)))
                .append('\n')
        }

        val needsSpecial = p3.notes.filter {
            it.contains("64-bit", true) ||
            it.contains("flex_bg", true) ||
            it.contains("checksums", true) ||
            it.contains("sparse_super", true)
        }
        if (needsSpecial.isNotEmpty()) {
            out.append("คุณสมบัติ EXT4 ที่ต้องจัดการก่อนย่อ:\n")
            needsSpecial.forEach { out.append("• ").append(it).append('\n') }
        }
        if (p3.highAllocatedBlock == null)
            out.append("ตำแหน่ง Metadata สูงสุด: ไม่ได้ยืนยัน • ห้ามตัดดิสก์ตามจำนวน MB ที่ใช้\n")

        out.append("สถานะการสร้าง IMG ย่อขนาดจริง: BLOCKED\n")
        out.append("ยังไม่รวม e2fsck/resize2fs ที่ผ่านการตรวจบน Android " +
            "และยังไม่ตรวจ CRC ของ EXT4 ครบทุกบล็อก\n")
        out.append("ห้ามตัดข้อมูลท้ายพาร์ทิชันแบบ Raw หรือเติมศูนย์แทน Read Error\n")
        out.append("ต้องตรวจ IMG ใหม่, SHA-256 ของข้อมูล และบูต NAS จริงบน USB สำรอง\n")
        return out.toString()
    }
}
