package com.pcopi.usbimager

import android.content.Context
import java.io.File

/**
 * Planning-only geometry check. No USB is opened and no IMG is written.
 * The recorded layout MUST be compared against the real source MBR before export.
 */
internal object CompactImgPlan {
    data class Report(val passed:Boolean,val text:String)
    private const val SECTOR=512L
    private const val P1_START=2048L
    private const val P1_END=104447L
    private const val P2_START=104448L
    private const val P2_END=206847L
    private const val P3_START=206848L
    private const val ARC3_BYTES=2147483648L
    fun run(context:Context):Report {
        val out=StringBuilder("=== Compact IMG Geometry Preview v0.2.31 ===\n")
        out.append("PLAN ONLY: no USB access, no IMG created, no source modified\n")
        val workspace=File(context.filesDir,"arc3-workspace-v0221/arc3-working.ext4")
        val mirror=File(context.filesDir,"legacy-arc3-mirror-v0220/arc3-mirror.ext4")
        if(!workspace.isFile || workspace.length()!=ARC3_BYTES || !mirror.isFile ||
            mirror.length()<=workspace.length())
            return Report(false,out.append("BLOCKED: preserved ARC3 mirror/workspace sizes invalid").toString())
        if(P1_START<=0 || P1_END+1!=P2_START || P2_END+1!=P3_START ||
            ARC3_BYTES%SECTOR!=0L)
            return Report(false,out.append("BLOCKED: recorded partition geometry invalid").toString())
        val p3Sectors=ARC3_BYTES/SECTOR
        val end=P3_START+p3Sectors-1
        val totalSectors=end+1
        val imageBytes=totalSectors*SECTOR
        out.append("Recorded source layout (NOT independently verified):\n")
        out.append("P1: LBA ").append(P1_START).append("-").append(P1_END).append("\n")
        out.append("P2: LBA ").append(P2_START).append("-").append(P2_END).append("\n")
        out.append("P3: LBA ").append(P3_START).append("-").append(end)
            .append(" (").append(p3Sectors).append(" sectors)\n")
        out.append("Planned IMG sectors: ").append(totalSectors).append("\n")
        out.append("Planned IMG bytes: ").append(imageBytes).append("\n")
        out.append("Planned IMG GiB: ").append("%.3f".format(java.util.Locale.US,
            imageBytes.toDouble()/1073741824.0)).append("\n")
        out.append("BLOCKED FOR EXPORT: source MBR, boot code, P1/P2 contents, ")
        out.append("partition types and IDs still need byte-level acquisition and validation.\n")
        out.append("PASS: geometry arithmetic only. No bootability claim.")
        return Report(true,out.toString())
    }
}
