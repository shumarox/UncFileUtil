package ice.util.win;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.Arrays;
import java.util.List;

public class SHARE_INFO_1 extends Structure {
    public String name;
    public int shareType;
    public String remark;

    public SHARE_INFO_1() {
        this(null);
    }

    public SHARE_INFO_1(Pointer memory) {
        super(memory, Structure.ALIGN_DEFAULT, W32APITypeMapper.UNICODE);
    }

    protected List<String> getFieldOrder() {
        return Arrays.asList("name", "shareType", "remark");
    }

    @Override
    public String toString() {
        return name;
    }
}

