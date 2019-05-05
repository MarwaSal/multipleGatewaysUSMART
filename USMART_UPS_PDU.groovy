import org.arl.unet.PDU 
import java.nio.ByteOrder

public class USMART_UPS_PDU extends PDU {
    void format() {
      length(14);                    // 16 byte PDU
      order(ByteOrder.BIG_ENDIAN);   // byte ordering is big endian
      //int8("cycle");                 // cycle #id, to avoid mixing up messages in different cycles for
      uint16("delay");                // 2 byte field "data" as unsigned short
      int16("xm");
      int16("ycm");
      int16("ym");
      int16("ycm");
      int16("zm");
      int16("zcm");
      padding(0xff);                 // padded with 0xff to make 16 bytes
    }
 }