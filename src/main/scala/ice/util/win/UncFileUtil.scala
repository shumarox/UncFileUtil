package ice.util.win

import java.io.{File, FileFilter, FilenameFilter}
import java.util.Date

import com.sun.jna._
import com.sun.jna.platform.win32.WinBase.WIN32_FIND_DATA
import com.sun.jna.platform.win32.{Kernel32, Win32Exception, WinNT}
import com.sun.jna.ptr.{IntByReference, PointerByReference}
import com.sun.jna.win32.W32APIOptions

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks

object UncFileUtil {
  def main(args: Array[String]): Unit = {
    val targets = if (args == null || args.isEmpty) Array("\\\\localhost") else args

    targets.foreach { target =>
      val files = listFiles(target)

      files.foreach { file =>
        println(new Date(file.lastModified()).toString + "\t" + file.getAbsolutePath)
      }
    }
  }

  def listFiles(path: String): List[NFileBase] =
    if (path.matches("^\\\\\\\\[^\\\\]+$")) {
      listNetShares(path.drop(2)).map(new SFile(path, _))
    } else {
      listFilesStandard(path)
    }

  def listFilesStandard(path: String): List[NFile] = {
    val buffer: ListBuffer[NFile] = ListBuffer[NFile]()

    def appendToBuffer(p: Pointer): Unit = {
      val data = new WIN32_FIND_DATA(p)
      val foundFileName = data.getFileName
      if (List(".", "..").contains(foundFileName)) return
      buffer.addOne(new NFile(s"$path${File.separator}$foundFileName", data))
    }

    val pathForFindFirstFile = if (path.startsWith("\\\\")) s"\\\\?\\UNC\\${path.drop(2)}\\*" else s"\\\\?\\$path\\*"

    val p = new Memory(WIN32_FIND_DATA.sizeOf())
    val hnd = Kernel32.INSTANCE.FindFirstFile(pathForFindFirstFile, p)

    if (hnd.getPointer != new Pointer(-1)) {
      try {
        appendToBuffer(p)

        Breaks.breakable {
          while (true) {
            val p = new Memory(WIN32_FIND_DATA.sizeOf())
            if (!Kernel32.INSTANCE.FindNextFile(hnd, p)) Breaks.break()
            appendToBuffer(p)
          }
        }
      } finally {
        Kernel32.INSTANCE.FindClose(hnd)
      }
    }

    buffer.toList
  }

  private trait Netapi32 extends Library {
    def NetShareEnum(serverName: String, level: Int, bufPtr: PointerByReference, prefMaxLen: Int,
                     entriesRead: IntByReference, totalEntries: IntByReference, resumeHandle: IntByReference): Int

    def NetApiBufferFree(buffer: Pointer): Int
  }

  private val Netapi32: Netapi32 = Native.load("netapi32", classOf[Netapi32], W32APIOptions.DEFAULT_OPTIONS)

  def listNetShares(server: String): List[String] = {
    val bufPtr = new PointerByReference
    val entriesRead = new IntByReference
    val totalEntries = new IntByReference
    val resumeHandle = new IntByReference

    val result = Netapi32.NetShareEnum(server, 1, bufPtr, 0xFFFFFFFF, entriesRead, totalEntries, resumeHandle)

    if (result != 0) throw new Win32Exception(result)

    val list =
      try {
        new SHARE_INFO_1(bufPtr.getValue).toArray(entriesRead.getValue).asInstanceOf[Array[SHARE_INFO_1]].toList
      } finally {
        Netapi32.NetApiBufferFree(bufPtr.getValue)
      }

    list.filter(s => s.name != null && s.shareType == 0).map(s => s.name)
  }
}

abstract class NFileBase(path: String) extends File(path) {

  override def canRead: Boolean = true

  override def canWrite: Boolean = false

  override def exists: Boolean = true

  override def isHidden: Boolean = false

  override def list: Array[String] = throw new UnsupportedOperationException

  override def list(filter: FilenameFilter): Array[String] = throw new UnsupportedOperationException

  override def listFiles: Array[File] = UncFileUtil.listFiles(this.getAbsolutePath).toArray

  override def listFiles(filter: FilenameFilter): Array[File] = listFiles.filter(f => filter.accept(this, f.getName))

  override def listFiles(filter: FileFilter): Array[File] = listFiles.filter(filter.accept)

  override def canExecute: Boolean = false
}

class NFile(path: String, data: WIN32_FIND_DATA) extends NFileBase(path) {

  override def getName: String = data.getFileName

  override def isDirectory: Boolean = (data.dwFileAttributes & WinNT.FILE_ATTRIBUTE_DIRECTORY) > 0

  override def isFile: Boolean = !isDirectory

  override def isHidden: Boolean = false

  override def lastModified: Long = data.ftLastWriteTime.toTime

  override def length: Long = data.nFileSizeHigh * 0x100000000L + data.nFileSizeLow
}

class SFile(parent: String, name: String) extends NFileBase(s"$parent${File.separator}$name") {
  override def isDirectory: Boolean = true

  override def isFile: Boolean = false

  override def lastModified: Long = -1

  override def length: Long = 0
}

class HFile(path: String) extends NFileBase(path) {
  override def isDirectory: Boolean = true

  override def isFile: Boolean = false

  override def lastModified: Long = -1

  override def length: Long = 0
}
