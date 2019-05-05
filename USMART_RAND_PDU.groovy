import org.arl.unet.PDU 
import java.nio.ByteOrder

public class USMART_RAND_PDU extends PDU {
    void format() {
      length(14);                    // 16 byte PDU
      order(ByteOrder.BIG_ENDIAN);   // byte ordering is big endian
      
      uint8("n0");  
      uint8("n1"); 
      uint8("n2"); 
      uint8("n3"); 
      uint8("n4"); 
      uint8("n5"); 
      uint8("n6"); 
      uint8("n7"); 
      uint8("n8"); 
      uint8("n9"); 
      uint8("n10"); 
      uint8("n11"); 
      uint8("n12"); 
      //uint8("n13");
      uint8("nslots");
      
     padding(0xff);                 // padded with 0xff to make 16 bytes
    }
 }