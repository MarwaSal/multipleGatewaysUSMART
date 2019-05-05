import org.arl.fjage.Message
import org.arl.unet.*
import org.arl.unet.phy.RxFrameNtf
import org.arl.unet.phy.TxFrameNtf
 
class USMARTAnchorDaemon extends UnetAgent { 
  
  def USMART_UPS_PDU pdu = new USMART_UPS_PDU()
  def phy
  def myLocation
  def myAddress
  def nodeInfo
  def tr
  def ta1, ta2
  def ts1, ts2
  
  void startup() {

    //println "In USMARTAnchorDaemon::startup"
 
    //println "Getting NODE_INFO "
    nodeInfo = agentForService Services.NODE_INFO
    //println("nodeInfo =  " + nodeInfo)
    //println "Getting location"
    myLocation = nodeInfo.location
    myAddress = nodeInfo.address
    //println "location is "+ myLocation
    
    phy = agentForService Services.PHYSICAL
    subscribe topic(phy)
  }
  
  void processMessage(Message msg) {
    //println "In USMARTAnchorDaemon::processMessage"

    if (msg instanceof RxFrameNtf && msg.protocol == Protocol.RANGING && msg.from == (myAddress-1))  {
        //println("USMARTAnchorDaemon "+ myAddress+" receiving rxframentfn from " + msg.from +" msg rx time is "+ msg.rxTime)
        ta1 = nanoTime()
    }
    
    if (msg instanceof TxFrameNtf)  {
        println("USMARTAnchorDaemon "+ myAddress+" receiving tframentfn  tx time is "+ msg.txTime +" DELAY IS "+(msg.txTime-tr))
    }

    if (msg instanceof DatagramNtf && msg.protocol == Protocol.RANGING && msg.from == (myAddress-1)) {
        tr = msg.getRxTime()
        println("USMARTAnchorDaemon "+ myAddress+" receiving message from " + msg.from +" protocol is "+ msg.protocol +" rx time is "+tr + " agent time is "+ta1)
        sendUPSBeacon()
    }
    
  }

  void sendUPSBeacon() {
    //println "In USMARTAnchorDaemon::sendPSBeacon() "
    def DatagramReq req = new DatagramReq(to: Address.BROADCAST, protocol: Protocol.RANGING)

    short _xm = (short)Math.floor(myLocation[0])
    short _xcm =  (short)Math.floor((myLocation[0] - _xm)/0.01)
    short _ym = (short)Math.floor(myLocation[1])
    short _ycm = (short)Math.floor((myLocation[1] - _ym)/0.01)
    short _zm = (short)Math.floor(myLocation[2])
    short _zcm = (short)Math.floor((myLocation[2] - _zm)/0.01)

    //def delay = nodeInfo.time - tr
    //if (t) delay = nanoTime() - t // TO-DO: should add/consider some extra delay for the time to encode, call setData and start transmitting
    // IDEA, CAN MEASURE (it will receive the tx notification, it will be late the first time but not the second, can even use a filter...)
    // or send in one cycle the delay of the last one
    // I also need to take care of not mixing cycles 
    ta2 = nanoTime()
    def int DELAY = (ta2-ta1)/1000
    println("USMARTAnchorDaemon DELAY AGENT NANOTIME IS "+DELAY)
    def bytes = pdu.encode([delay: DELAY, xm: _xm, xcm: _xcm, ym: _ym, ycm: _ycm, zm: _zm, zcm: _zcm])
    req.setData(bytes)

    phy << req
  }
}