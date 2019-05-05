
import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.sim.*
import org.arl.unet.sim.channels.*
import static org.arl.unet.Services.*
import Jama.Matrix


class USMARTSensorDaemon extends UnetAgent {

  
  final static int PING_PROTOCOL = Protocol.USER
  final static int TDI_PROTOCOL = Protocol.DATA
  final static int REQ_PROTOCOL = 2
  final static int RANDOM_SENSE_PROTOCOL = 3
  final static int LOC_PROTOCOL = Protocol.RANGING

  def USMART_UPS_PDU pdu = new USMART_UPS_PDU()
  def USMART_SENSE_PDU spdu = new USMART_SENSE_PDU()
  def USMART_TDA_PDU tpdu = new USMART_TDA_PDU()
  //def USMART_REQ_PDU rpdu = new USMART_REQ_PDU()
  //def USMART_RAND_PDU npdu = new USMART_RAND_PDU()
  def USMART_TIME_PDU tipdu = new USMART_TIME_PDU()
  
  public duration
  public phy
  
  def myLocation
  def myName
  def myAddress
  def nodeInfo
  def myGateway
  def t
  def noMessagesSent =0
  def wTime =[]
  def v = 1500
  def t1,t2,t3,t4
  int cn =0
  def dtB, dtC, dtD
  def A0 = new Matrix(3,1)
  def A1 = new Matrix(3,1)
  def A2 = new Matrix(3,1)
  def A3 = new Matrix(3,1)
  def S = new Matrix(3,1)
  def double TIME_OFFSET = 2.7 //69 // 2.8
  def  double nodeTime
  def strg=0
  def gateways = 1..4
  def locHypothesis = []
  def locError
  def File fileStats
  def File copyFileStats
  def gatewaySubsetOfNodes= 0
  File framesTimeStamp = new File('.','timeStamp.txt')  
  File Gateway = new File('.','nodeGateway.txt')
  String line
 
   USMARTSensorDaemon(myGateway) {
    this.myGateway= myGateway                       
   }                                
  
  void startup() {
    fileStats = new File('.','nodeStats.txt') 
    copyFileStats = new File('.','copyNodeStats.xls') 
    println "In USMARTSensorDaemon::startup"
    
    println "Getting NODE_INFO "
    nodeInfo = agentForService Services.NODE_INFO
   
    println("nodeInfo =  " + nodeInfo)
    println "Getting location"
    myLocation = nodeInfo.location
    myAddress = nodeInfo.address
    println "location is "+ myLocation
    
   println"node "+myAddress+" gateway: "+myGateway
   
    phy = agentForService Services.PHYSICAL
    subscribe topic(phy)
    duration = Math.round(((phy[Physical.DATA].frameDuration) *1000 ))
    
  

  }

  void processMessage(Message msg) {

    //if (msg instanceof RxFrameNtf)
    //  println("Sensor "+ myAddress+" receiving rxframentfn from " + msg.from +" msg rx time is "+ msg.rxTime)

    if (msg instanceof DatagramNtf) 
      println("Sensor "+ myAddress+" receiving message from " + msg.from +" protocol is "+ msg.protocol)


    if (msg instanceof DatagramNtf && msg.protocol == PING_PROTOCOL) {
      send new DatagramReq(recipient: msg.sender, to: msg.from, protocol: Protocol.USER)
      //println("counter on node script is"+cn++)
    }
      

    if (msg instanceof DatagramNtf && msg.protocol == TDI_PROTOCOL && (wTime[myAddress]==null)) { 

        println("Sensor "+ myAddress+" receiving TDI from " + msg.from +" protocol is "+ msg.protocol)
        //This is the TDI for TDMA access
        def data = tpdu.decode(msg.data)
        wTime[myAddress] = data.get('value')
        println"wTime: " + wTime[myAddress]    
    }

    if (msg instanceof DatagramNtf &&  msg.protocol == REQ_PROTOCOL && (wTime[myAddress]!= null) ) {  

        println("Sensor "+ myAddress+" receiving REQ from " + msg.from +" protocol is "+ msg.protocol)
        TDMASensing(myAddress, msg)
        if(gatewaySubsetOfNodes) wTime[myAddress]= null

        
        } 
      if (msg instanceof RangeNtf )
      {   
        float neighbor_distance = msg.getRange();
         println("node "+myAddress+ " gatwaydistance: "+ neighbor_distance+" gateway is: "+msg.from)
     }
       
    if (msg instanceof DatagramNtf && msg.protocol == RANDOM_SENSE_PROTOCOL) { 
      
      

      def data = spdu.decode(msg.data)
      def p = data.get('p')/100.0
      def nslots = data.get('nslots')
             
      def phy = agentForService Services.PHYSICAL
      //in single gateway secnario nodes send to gatway 1 which is located in the middle of the surface
      def txReq = new DatagramReq(recipient: msg.sender, to: myGateway, protocol: Protocol.DATA);
      def duration = 62  // TO-DO: Math.round(1000*phy[0].frameDuration)   // duration in ms
      //println("Frame duration is "+phy[0].frameDuration+" [sec] , i.e. " + duration +" [ms]")
       println("Frame duration is "+phy[Physical.DATA].frameDuration+" [sec] , i.e. " + duration +" [ms]")
       
      //def busy = phy.busy
      //if (busy == null) log.warning 'BUSY FAILED!'
      //if (!busy) {
         // send it if modem is not TX/RX
      //   def rsp = phy << txReq
      //   if (rsp == null) log.warning 'TX FAILED!'
      //} else {
         // back-off and keep trying until modem is idle
         def rn = AgentLocalRandom.current().nextDouble(0, 1)
         //println("random of " + myAddress +" is " +rn)
         
         if (rn <= p) {
           def bo = AgentLocalRandom.current().nextInt(nslots)+1
           //def bo = myAddress;
           //println("sending data from " + myAddress +" at time " +bo*duration +" ms")
           add new BackoffBehavior(bo*duration, {
              def busy1 = phy.busy
             if (busy1 == null) log.info 'BUSY FAILED!'
              else if (busy1) {
                    //def bo1 = AgentLocalRandom.current().nextInt(nslots)+1
                   println("just to check the chennel is busy but node " + myAddress +" will send anyway, its wait time is " +bo*duration +" ms")
                  // backoff(bo1*duration)
                   //println("backoff of " + myAddress +" failed")
                 } 
              //  else {
                  
                  phy << new ClearReq()
                  noMessagesSent++
                  nodeTime= Math.round(nanoTime()/1000000)
                 fileStats<< "USMARTSensorDaemon  node, "+ myAddress+" ,is  sending, "+ noMessagesSent +" ,"+ nodeTime +" ,ms \n"
                 copyFileStats<< "USMARTSensorDaemon  node, "+ myAddress+" ,is  sending, "+ noMessagesSent +" ,"+ nodeTime +" ,ms \n"
                 println( "USMARTSensorDaemon  node, "+ myAddress+" ,is  sending "+ noMessagesSent +"msg to: "+myGateway+" at time: "+ nanoTime() +" ,ms")
                 def rsp1 = phy << txReq
                  if (rsp1 == null) log.info 'TX FAILED!'
               // }
          })
         }
    }

// WITH T
/*
T2: 4450133123835dtB: 25.495 in (2400.0, 1600.0, -5.0 )
T3: 4453790456215dtC: 53.086 in (2000.0, 2400.0, -5.0 )
T4: 4456769333460dtD: 28.436 in (2000.0, 2000.0, -500.0 )

// WITH TXFRAME
T2: 190498070dtB: 37.98 in (2400.0, 1600.0, -5.0 )
T3: 194049487dtC: 22.039 in (2000.0, 2400.0, -5.0 )
T4: 196918328dtD: 16.151 in (2000.0, 2000.0, -500.0 )

T2: 3370229002dtB: 21.145 in (2400.0, 1600.0, -5.0 )
T3: 3373785419dtC: 18.745 in (2000.0, 2400.0, -5.0 )
T4: 3376657260dtD: 11.517 in (2000.0, 2000.0, -500.0 )


// WITH DATE

T2: 1532242539616dtB: 61.758 in (2400.0, 1600.0, -5.0 )
T3: 1532242543271dtC: 44.639 in (2000.0, 2400.0, -5.0 )
T4: 1532242546248dtD: 41.647 in (2000.0, 2000.0, -500.0 )
*/

    if (msg instanceof DatagramNtf && msg.protocol == LOC_PROTOCOL) {
         //t = nanoTime()
         t = msg.getRxTime()
         //t = nodeInfo.time
         
         //println("Sensor "+ myAddress+" receiving localisation message from " + msg.from +" protocol is "+ msg.protocol +" rssi is "+ msg.rssi + " data is "+msg.data+" time is "+ t)
         //println("Sensor "+ myAddress+" receiving localisation message from " + msg.from +" protocol is "+ msg.protocol + " data is "+msg.data+" time is "+ t +" date is "+ nodeInfo.time)

         double delay = 0
         double x = 0.0
         double y = 0.0
         double z = 0.0
         if (msg.data) {
             def data = pdu.decode(msg.data)
             delay = data.get('delay')/1000.0/1000.0
             x = (double)data.get('xm')//+(double)0.01*data.get('xcm') TO-DO
             y = (double)data.get('ym')//+(double)0.01*data.get('ycm')
             z = (double)data.get('zm')//+(double)0.01*data.get('zcm')

             //println("Sensor "+ myAddress+" receiving message from " + msg.from +" protocol is "+ msg.protocol +" msg is "+x+","+y+","+z+" delay:"+delay)
         }

         switch (msg.from) {
             case 1:
                t1 = t 
                //println("T1: "+ t1) //TO-DO
                A0.set(0,0,1600) 
                A0.set(1,0,1600)
                A0.set(2,0, -5)
                break
             case 2:
                t2 = t 
                dtB = delay
                A1.set(0,0,x) 
                A1.set(1,0,y)
                A1.set(2,0,z)
                //println("T2: "+ t2 + "dtB: "+ dtB + " in ("+x+", "+y+", "+z+" )")
                break
             case 3:
                t3 = t 
                dtC = delay
                A2.set(0,0,x) 
                A2.set(1,0,y)
                A2.set(2,0,z)
                //println("T3: "+ t3 + "dtC: "+ dtC + " in ("+x+", "+y+", "+z+" )")
                break
             case 4:
                t4 = t 
                dtD = delay
                A3.set(0,0,x) 
                A3.set(1,0,y)
                A3.set(2,0,z)
                //if (!t1) println("I did not receive beacon from node #1") 
                //if (!t2) println("I did not receive beacon from node #2") 
                //if (!t3) println("I did not receive beacon from node #3") 
                //println("T4: "+ t4 + "dtD: "+ dtD + " in ("+x+", "+y+", "+z+" )")
                if (t1 && t2 && t3) {
                  localise() 
                }
                break
         }
         
    }
   
  }


   void TDMASensing(addr,msg) {
  //String time=nanoTime().toString()
      def txReq = new DatagramReq(recipient: msg.sender, to: 1, protocol: Protocol.DATA )
      println("Frame duration is "+phy[Physical.DATA].frameDuration+" [sec] , i.e. " + 367.5 +" [ms] and waiting time is " +wTime[myAddress] +" ms")
      // this works for eithor for full schedul for static TDMA, or subschedul for dynamic TDMA calulated for a subset of nodes
     def timeStamp = Math.round(nanoTime()/1000000) 
      add new WakerBehavior(wTime[myAddress], {
      def busy1 = phy.busy
      if (busy1 == null) log.warning 'BUSY FAILED!'
                 if (busy1) {
                    println"modem is busy when node " +myAddress+" tried to send"
                 } 
                 //else {
                
                 
                  phy << new ClearReq()
                  
                  noMessagesSent++
                  
                  def bytes = tipdu.encode(time:timeStamp )
                  txReq.setData(bytes)
                  println("sending data from " + myAddress+" node tims is: "+nanoTime()/1000000)
                  framesTimeStamp<< myAddress +","+ noMessagesSent +","+nanoTime()+"\n"
                  def rsp1 = phy << txReq
                //  finishEnergyCalculation()
                 
                //  println "Node: "+myAddress+" txTime= "+txReq.txTime
                  if (rsp1 == null) log.warning 'TX FAILED!'
               // }
           }) 
         }

  


  void addLocHypothesis(Matrix X) {
    Matrix knownLocation = new Matrix(3, 1)
    knownLocation.set(0,0, myLocation[0])
    knownLocation.set(1,0, myLocation[1])
    knownLocation.set(2,0, myLocation[2])

    locHypothesis.add(X)
    locError = X.minus(knownLocation).normInf()
    println("LOCATION COMPUTED: ("+ X.get(0,0)+ ", "+ X.get(1,0)+ ", "+X.get(2,0)+") Real is ("+ myLocation[0]+ ", "+ myLocation[1]+ ", "+myLocation[2]+") Error is"+locError)
    
  }
  
  void localise() {
     println("IN LOCALIZATION OF SENSOR #"+ myAddress)
     
     def M = new Matrix(3, 3)
     def C = new Matrix(3, 1)
     def D = new Matrix(3, 1)

     M.set(0,0, A0.get(0,0)-A1.get(0,0))
     M.set(0,1, A0.get(1,0)-A1.get(1,0))
     M.set(0,2, A0.get(2,0)-A1.get(2,0))
     M.set(1,0, A0.get(0,0)-A2.get(0,0))
     M.set(1,1, A0.get(1,0)-A2.get(1,0))
     M.set(1,2, A0.get(2,0)-A2.get(2,0))
     M.set(2,0, A0.get(0,0)-A3.get(0,0))
     M.set(2,1, A0.get(1,0)-A3.get(1,0))
     M.set(2,2, A0.get(2,0)-A3.get(2,0))

     double dAB = Math.sqrt(M.get(0,0)*M.get(0,0)+M.get(0,1)*M.get(0,1)+M.get(0,2)*M.get(0,2))
     double dAC = Math.sqrt(M.get(1,0)*M.get(1,0)+M.get(1,1)*M.get(1,1)+M.get(1,2)*M.get(1,2))
     double dAD = Math.sqrt(M.get(2,0)*M.get(2,0)+M.get(2,1)*M.get(2,1)+M.get(2,2)*M.get(2,2))
     
     double dBC = Math.sqrt( 
      (A1.get(0,0)-A2.get(0,0))*(A1.get(0,0)-A2.get(0,0)) +
      (A1.get(1,0)-A2.get(1,0))*(A1.get(1,0)-A2.get(1,0)) +
      (A1.get(2,0)-A2.get(2,0))*(A1.get(2,0)-A2.get(2,0)))

     double dCD = Math.sqrt( 
      (A2.get(0,0)-A3.get(0,0))*(A2.get(0,0)-A3.get(0,0)) +
      (A2.get(1,0)-A3.get(1,0))*(A2.get(1,0)-A3.get(1,0)) +
      (A2.get(2,0)-A3.get(2,0))*(A2.get(2,0)-A3.get(2,0)))

     /*
     C.set(0, 0, ((t1-t2)/1.0e9+TIME_OFFSET*1)*v+dAB) 
     C.set(1, 0, ((t1-t3)/1.0e9+TIME_OFFSET*2)*v+dAB + dBC) 
     C.set(2, 0, ((t1-t4)/1.0e9+TIME_OFFSET*3)*v+dAB + dBC + dCD) 
             */

     //C.set(0, 0, ((t1-t2)/1.0e9+TIME_OFFSET*1)*v+dAB) 
     //C.set(1, 0, ((t1-t3)/1.0e9+TIME_OFFSET*2)*v+dAB + dBC) 
     //C.set(2, 0, ((t1-t4)/1.0e9+TIME_OFFSET*3)*v+dAB + dBC + dCD) 

     //LAST
     //C.set(0, 0, ((t1-t2)/1.0e6+TIME_OFFSET*1)*v+dAB) 
     //C.set(1, 0, ((t1-t3)/1.0e6+TIME_OFFSET*2)*v+dAB + dBC) 
     //C.set(2, 0, ((t1-t4)/1.0e6+TIME_OFFSET*3)*v+dAB + dBC + dCD) 

/*
     t1 = Atransmissiontime + prop(AS) + duration
     t2 = Atranmissiontime + prop(AB) + duration + prop(BS) + duration
     t3 = Atransmission + prop(AB) + duration + prop(BC) + duration + prop(CS) + duration

     t1-t2 = p(AS) - prop(AB) - prop(BS) - D
     prop(BS)v = prop(AS)v + (t2-t1)v - prop(AB)v - Duratiob*v
     prop(BS)*v = prop(AS)*v + (t2-t1)v - dAB - Duration*v
   */   

/*
1533273486744|INFO|Script1@11:invoke|Created static node 1 (1) @ [1600, 1600, -5]
1533273486761|INFO|Script1@11:invoke|Created static node 2 (2) @ [2400, 1600, -5]
1533273486764|INFO|Script1@11:invoke|Created static node 3 (3) @ [2000, 2400, -5]
1533273486770|INFO|Script1@11:invoke|Created static node 4 (4) @ [2000, 2000, -500]
1533273486800|INFO|Script1@11:invoke|Created static node 5 (5) @ [2500, 1800, -480]
*/
     double dAS = Math.sqrt((1600-2500)*(1600-2500)+(1600-1800)*(1600-1800)+(-480+5)*(-480+5))
     double dBS = Math.sqrt((2400-2500)*(2400-2500)+(1600-1800)*(1600-1800)+(-480+5)*(-480+5))
     double dCS = Math.sqrt((2000-2500)*(2000-2500)+(2400-1800)*(2400-1800)+(-480+5)*(-480+5))
     double dDS = Math.sqrt((2000-2500)*(2000-2500)+(2000-1800)*(2000-1800)+(-480+500)*(-480+500))

     C.set(0, 0, ((t1-t2)/1.0e6+TIME_OFFSET*1+dtB)*v+dAB)                             
     C.set(1, 0, ((t1-t3)/1.0e6+TIME_OFFSET*2+dtB+dtC)*v+dAB + dBC) 
     C.set(2, 0, ((t1-t4)/1.0e6+TIME_OFFSET*3+dtB+dtC+dtD)*v+dAB + dBC + dCD) 

     println("t1-t2= "+(t1-t2)/1.0e6)
     println("t1-t3= "+(t1-t3)/1.0e6)
     println("t1-t4= "+(t1-t4)/1.0e6)
   
     println("k1= "+C.get(0,0) + " :" + (dAS-dBS))
     println("k2= "+C.get(1,0) + " :"+ (dAS-dCS))
     println("k3= "+C.get(2,0) + " :"+ (dAS-dDS))

     //C.set(0,0, (dAS-dBS))
     //C.set(1,0, (dAS-dCS))
     //C.set(2,0, (dAS-dDS))

     C.set(0,0, 518)
     C.set(1,0, 139.24)
     C.set(2,0, 515.489)


     double so = A0.transpose().times(A0).get(0,0);
     double s1 = A1.transpose().times(A1).get(0,0);
     double s2 = A2.transpose().times(A2).get(0,0);
     double s3 = A3.transpose().times(A3).get(0,0);
     
     D.set(0, 0, s1 - so - C.get(0,0)*C.get(0,0))
     D.set(1, 0, s2 - so - C.get(1,0)*C.get(1,0))
     D.set(2, 0, s3 - so - C.get(2,0)*C.get(2,0))
     
     M.timesEquals(2)
     C.timesEquals(-2)
     D.timesEquals(-1)
     Matrix A = M.solve(C)
     Matrix B = M.solve(D)

     println("M:\n===================================================\n")
     println(M.get(0,0) + "  " + M.get(0,1) + " " + M.get(0,2))
     println(M.get(1,0) + "  " + M.get(1,1) + " " + M.get(1,2))
     println(M.get(2,0) + "  " + M.get(2,1) + " " + M.get(2,2))

     println("C = ["+C.get(0,0)+","+C.get(1,0)+","+C.get(2,0)+"]")
     println("D = ["+D.get(0,0)+","+D.get(1,0)+","+D.get(2,0)+"]")
  
     println("A = ["+A.get(0,0)+","+A.get(1,0)+","+A.get(2,0)+"]")
     println("B = ["+B.get(0,0)+","+B.get(1,0)+","+B.get(2,0)+"]")
    
     
     double a = A.transpose().times(A).get(0,0)-1
     //beta = 2*A.dot(B) - 2*A.dot(P[0])
     double b = 2*A.transpose().times(B).minus(A.transpose().times(A0)).get(0, 0)
     double c = B.transpose().times(B).get(0,0)+A0.transpose().times(A0).get(0,0) -B.transpose().times(2).times(A0).get(0, 0)
     double det = b*b - 4*a*c

     println("alpha is "+a)
     println("beta is "+b)
     println("gamma is "+c)
     println("det is "+det)
     
     if (det >= 0) {
        double sqr_det = Math.sqrt(det)
        double d0_1 = (-b + sqr_det)/(2*a)
        double d0_2 = (-b - sqr_det)/(2*a)
    
        if (d0_1 > 0) {
          def X1 = A.times(d0_1).plus(B)
          addLocHypothesis(X1)
        }

        if (d0_2 > 0) {
          def X2 = A.times(d0_2).plus(B)
          addLocHypothesis(X2)
        }
    
        /*
         
  for r in roots:
      # eliminate negative solutions
      if r < 0:
        continue
      # calculate the positon
      pos = A*r + B
      # check it's at a valid distance from each anchor
      if max([ np.linalg.norm(pos-a) for a in P ]) <= SIM_RANGE * 1.1:
        positions.append(pos)
    # return the result
    if len(positions) == 0:
      return "no result", np.zeros(3)
    elif len(positions) == 1:
      return "ok", positions[0]
    else:
      print ('Multiple solutions found:')
      print ('  ', str(positions[0]))
      
         */
     }
     //MA + C = 0
     //MB + C = 0
     
    
  }
}