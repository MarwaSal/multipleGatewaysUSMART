import org.arl.unet.PDU 
import java.nio.ByteOrder

public class USMART_TIME_PDU extends PDU {
    void format() {
      length(5);                    // 16 byte PDU
      order(ByteOrder.BIG_ENDIAN);   // byte ordering is big endian
      //int8("cycle");                 // cycle #id, to avoid mixing up messages in different cycles for
      uint16("time");                // 2 byte field "data" as unsigned short
      
  
      padding(0xff);                 // padded with 0xff to make 16 bytes
    }
 }