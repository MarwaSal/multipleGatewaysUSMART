import org.arl.fjage.*
import org.arl.fjage.Message.*
import org.arl.unet.*
import org.arl.unet.sim.*
import org.arl.unet.phy.BeaconReq 
import org.arl.unet.phy.RxFrameNtf
import org.arl.unet.phy.TxFrameNtf

class USMARTBaseAnchorDaemon extends UnetAgent {

  def USMART_UPS_PDU pdu = new USMART_UPS_PDU()
  def USMART_SENSE_PDU spdu = new USMART_SENSE_PDU()
  def USMART_TDA_PDU tpdu = new USMART_TDA_PDU()
  def USMART_TIME_PDU tipdu = new USMART_TIME_PDU()
  def phy
  def myLocation
  def myAddress
  def nodeInfo
  def File fileFrames
  def File fileStats
  def t, k = 0, count = 1, i=0, j=0, gt=140000,  x=0, w=0, index=0, v=0, lastNode =0, p1=1
  def tp=[], keyTp =[], valueTp =[],keyTx=[],  valueTx =[], DkeyTp=[], DvalueTp=[], DvalueTx=[]
  def propagationDelay = [:], transmissionDelay= [:]
  def sumtp =0
  def allNodes=0, gatewaySubsetOfNodes =0, subsetRandomAccess =0, maxNum=20
  
 // def lines
  public int noMessagesReceived =0
  public double time
  public double time2
  public int txCountNs
  public int delay =0
  public double treq2 = 270888.297
  public double treq=0 // REQtime= 28380.6815 s

  // parameters
  int pSensing = 0
  int nSlots = 0
  int c=0
  int sumMsgSent =0
  

  USMARTBaseAnchorDaemon(int pSensing=0, int nSlots=100) {
    this.pSensing = pSensing                        
    this.nSlots = nSlots                                  
  }
  

  void setup() {
     register 'gateway' 
  }
  
  void startup() {
    //println "In USMARTBaseAnchorDaemon::startup"

    fileFrames = new File('.','frames.txt')      
    fileStats = new File('.','nodeStats.txt')     
     
    //println "Getting NODE_INFO "
    nodeInfo = agentForService Services.NODE_INFO
    //println("nodeInfo =  " + nodeInfo)
    //println "Getting location"
    myLocation = nodeInfo.location
    myAddress = nodeInfo.address
    //println "location is "+ myLocation

    phy = agentForService Services.PHYSICAL
    subscribe topic(phy)


    if(allNodes){
      
      log.info 'USMARTBaseAnchorDaemon::OneShotBehaviour calling ping()'
       for (addr in 5..204) {
          ping(addr)
         }
        log.info 'Origional list of propagation delays'
        println propagationDelay.keySet()
        println propagationDelay.values()
        log.info 'Sorted list of propagation delays'
        propagationDelay = propagationDelay.sort {it.value}
        println propagationDelay.keySet()
        println propagationDelay.values()
        println propagationDelay.size()
        //I want to store the values of the propgation delay map in two arrays for later use, one for the nodes (map keys), and one for the Tp (map values)
        for ( e in propagationDelay ) {
          println "key = ${e.key}, value = ${e.value}"
          keyTp[i]= e.key
          valueTp[i]= e.value
          sumtp= sumtp + e.value
          i=i+1  
          }
          println "total tp: "+sumtp
          TDA()
        }
        
/*
    else if (pSensing > 0) {
      add new OneShotBehavior({
        
        //  ping(5, 204)
        
        // behavior will be executed after all agents are initialized
        // agent is in RUNNING state
      
        //if (nslots != 0)  fileStats << p << " " << nslots << " " << noMessagesReceived << "\n"
      
        noMessagesReceived = 0
        txCountNs =0
     
        log.info 'USMARTBaseAnchorDaemon::OneShotBehaviour calling sense('+pSensing+','+nSlots+')...'
        sense(this.pSensing, this.nSlots)
      
     })  
    
    */
      
    add new MessageBehavior(GenericMessage, { req ->
      //println("In USMARTBaseAnchorDaemon::MessageBehavior req ="+req)
      //if (req.performative) println("req.performative is "+ req.performative)
      //else println("req.performative is null")
      
      if (req.performative == Performative.REQUEST) {
        
        //log.info "Generic message request of type ${req.type}"
        switch (req.type) {
            case 'loc':
              //println("Handling localisation request");
              sendUPSBeacon(); break;
            case 'ping':
              //println("Handling ping request");
              ping(req.from_addr, req.to_addr); break;
            case 'exe':
              //println("Handling exe request"); 
              exe(); break;
            case 'sense':
              //println("Handling sense request"); 
              sense(); break;
            default: println "Unknown request";
        }
        //println "In USMARTBaseAnchorDaemon::MessageBehavior, just after exe"
        def rsp = new GenericMessage(req, Performative.INFORM)
        rsp.ok = 1 
        //println "In USMARTBaseAnchorDaemon::MessageBehavior, rsp is " + rsp     
        send rsp
      }
    })

  }

  void processMessage(Message msg) {
    //println "In USMARTBaseAnchorDaemon::processMessage"
    //println "nodeInfo is "+ nodeInfo
     
    
   if (msg instanceof DatagramNtf) {
        noMessagesReceived ++
        k++
        println("USMARTBaseAnchorDaemon "+ myAddress+" receiving message "+noMessagesReceived+" from " + msg.from +" protocol is "+ msg.protocol +" node time is "+ nanoTime())
        fileFrames << "USMARTBaseAnchorDaemon "+ myAddress+" receiving "+ k +" message from " + msg.from +" protocol is "+ msg.protocol +" node time is "+nanoTime()+"\n"
        
        
        
        
        if ((noMessagesReceived >= (0.25*200)) && (c==0)){
        time = nanoTime()
        time2 =  Math.round(nanoTime()/1000000)
        def lines = fileStats.readLines()
        txCountNs = lines.size()
        println("the gateway received: " + noMessagesReceived+" packets, i.e 25% of the mesurements at: "+nanoTime()+ " i.e at: "+time2+ " ms and num of nodes participated: "+ txCountNs)
        fileFrames << "USMARTBaseAnchorDaemon "+ myAddress+" receiving "+ k +" i.e 25% of the mesurements from " + msg.from +" protocol is "+ msg.protocol +" node time is "+ nanoTime() +" ms and num of nodes participated: "+ txCountNs+"\n"
        fileStats.delete()
        c++
        } 
   }
  }


  void ping(addr) {
   //for (addr in from_addr .. to_addr) {
       println "Pinging ${addr} at ${nanoTime()}"

       DatagramReq req = new DatagramReq(to: addr, protocol: Protocol.USER)
       phy << req
       def txNtf = receive(TxFrameNtf, 50000) // TO-DO:check protocol
       def rxNtf = receive({ it instanceof RxFrameNtf && it.from == req.to}, 50000)
       if (txNtf && rxNtf && rxNtf.from == req.to) {
           println "Response from ${rxNtf.from}: "
           println "rxTime=${rxNtf.rxTime}"
           println "txTime=${txNtf.txTime}"
           println "Response from ${rxNtf.from}: time=${(rxNtf.rxTime-txNtf.txTime)/1000} ms"
        //   tp= (rxNtf.rxTime-txNtf.txTime)/1000  //Simulation time in Unetstack is in microsecond, easier to see the results in ms
           tp= (rxNtf.rxTime-txNtf.txTime)
           propagationDelay.put(addr, tp)   //save the node, along with its propagation delay (Tp)
           println "Response from: "+rxNtf.from+" time= "+tp+ " ms"   
           
       }
        else if(count < 5){
          count++
          println 'Request timeout, try again'
          ping(addr)
           }
        else println 'Request timeout, connection faild'
        count=1
    }
  //}


  void TDA()  {
      CalculateTx(propagationDelay.size(), keyTp, valueTp) 
       REQ()    //then the gateway brodcasts the REQ packets to all nodes in the network, so to ask the nodes to start to sense
    }


  void CalculateTx(schedulength, AkeyTp, AvalueTp){
    for (j=0; j<= (schedulength-1); j=j+1)  {
       if(j==0)   {
         valueTx[j] = 0 }  
       else{ 
         //println AkeyTp[j]+ " " + AvalueTp[j]+ " " +AvalueTp[j-1]+ " " - (AvalueTp[j]-AvalueTp[j-1]) // you can delete this line, this only to help me  see the calculation running right
      //   valueTx[j] = valueTx[j-1]+ 62 +gt-(AvalueTp[j]-AvalueTp[j-1])
          valueTx[j] = valueTx[j-1]+ 367.5 +gt-(AvalueTp[j]-AvalueTp[j-1])
       // println valueTx[j-1]+" "+ valueTx[j] //you can delete this line
         }
         
         //a map for the calculated transmission delays for the nodes
         transmissionDelay.put(AkeyTp[j], valueTx[j])   
         //}
         
          println "sending TDI to node: "+ AkeyTp[j]
        
         println valueTx[j]+" "+Math.round(valueTx[j].toDouble())
         
         DatagramReq req = new DatagramReq(to: AkeyTp[j], protocol: Protocol.DATA) // send the TDI to all nodes sequentialy according the sorted Tp
         def bytes = tpdu.encode(value: Math.round(valueTx[j].toDouble()))   
         req.setData(bytes)
         phy << req  
         
         }


      lastNode =  AkeyTp[j-1]    //this to help me calculate the Time interval for the REQ packet
     // treq= AvalueTp[j-1]+valueTx[j-1]+62+62
     treq= AvalueTp[j-1]+valueTx[j-1]+367.5+367.5
      println"max tp= "+AvalueTp[j-1]+" max tx= "+valueTx[j-1]+" RQUT= "+treq+" lastNode is: "+lastNode // you can delete this line
      println"The set of nodes along with thier calculated transmitt delay"
      println transmissionDelay.keySet()   // the whole schedule of nodes according to their Tx
      println transmissionDelay.values()  // the whole Tx values to be sent to all nodes, this is useful only if we want all nodes to sense, i.e static scheduling
      println transmissionDelay.size()
    }

   void REQ()  {
        DatagramReq req = new DatagramReq(to: Address.BROADCAST, protocol: 2)
        phy << req
       // def rxNtf = receive({ it instanceof RxFrameNtf && it.from == req.to}, 50000)
        //println rxNtf.timestamp()
        k = 0
        println"gateway brodcasting REQ packet to nodes"
        fileFrames << "USMARTBaseAnchorDaemon sending REQ packet for sensing request\n"
       }


    

  void sense() {
    sense(50, 100)
  }
  
  void sense(_p, _nslots) {
    DatagramReq req = new DatagramReq(to: Address.BROADCAST, protocol: 3)

    def bytes = spdu.encode([p: _p, nslots: _nslots])
    req.setData(bytes)
    phy << req
    k = 0
    fileFrames << "USMARTBaseAnchorDaemon sending sensing request\n"
  }
  
  void exe() {
       println "USMARTBaseAnchorDaemon::exe()"
  }
  
  void sendUPSBeacon() {
    //println "In SMARTBaseAnchorDaemon::sendUPSBeacon() "
    def DatagramReq req = new DatagramReq(to: Address.BROADCAST, protocol: Protocol.RANGING)

    /*
    println "location is "+ myLocation
    short _xm = (short)Math.floor(myLocation[0])
    short _xcm =  (short)Math.floor((myLocation[0] - _xm)/0.01)
    short _ym = (short)Math.floor(myLocation[1])
    short _ycm = (short)Math.floor((myLocation[1] - _ym)/0.01)
    short _zm = (short)Math.floor(myLocation[2])
    short _zcm = (short)Math.floor((myLocation[2] - _zm)/0.01)
*/
    short _xm = 1600
    short _xcm =  0
    short _ym = 1600
    short _ycm = 0
    short _zm = -5
    short _zcm = 0

    //def delay = 0
    //if (t) delay = nodeInfo.time - t // TO-DO: should add/consider some extra delay for the time to encode, call setData and start transmitting
    def bytes = pdu.encode([delay: 0, xm: _xm, xcm: _xcm, ym: _ym, ycm: _ycm, zm: _zm, zcm: _zcm])
    req.setData(bytes)

    phy << req
     
  }
  
}