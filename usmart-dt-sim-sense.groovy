//! Simulation: 3-node network with ping daemons
///////////////////////////////////////////////////////////////////////////////
///
/// To run simulation:
///   bin/unet samples/ping/ping-sim
///
///////////////////////////////////////////////////////////////////////////////

import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.sim.*
import org.arl.unet.sim.channels.*
import static org.arl.unet.Services.*
import static org.arl.unet.phy.Physical.*
import org.arl.fjage.RealTimePlatform
//import java.net.ServerSocket


///////////////////////////////////////////////////////////////////////////////
// display documentation

println '''
n-node network with sensing and localisation daemons
'''


///////////////////////////////////////////////////////////////////////////////
// simulator configuration

/*
channel = [
  model:                BasicAcousticChannel,
  carrierFrequency:     25.kHz,
  bandwidth:            4096.Hz,
  spreading:            1.5,
  temperature:          25.C,
  salinity:             35.ppt,
  noiseLevel:           60.dB,
  waterDepth:           20.m
]

modem.dataRate =        [800.bps, 2400.bps]
modem.frameLength =     [16.bytes, 64.bytes]
modem.powerLevel =      [0.dB, -10.dB]
*/

channel.model = ProtocolChannelModel
channel.soundSpeed = 1500.mps
channel.communicationRange = 3.km
channel.interferenceRange = 3.km

modem.dataRate = [640, 640].bps
modem.frameLength = [5, 5].bytes // the duration with the header and the correction code is 0.3675 s
//modem.frameLength = [7, 7].bytes // was 7
modem.headerLength = 0  //0.305 // the size in byte not the duration of the header+the error correction
modem.preambleDuration = 0
modem.txDelay = 0
modem.refPowerLevel   = 168


//def nodes = 5..5
//def nodes = 5..10                     // list of nodes, just one to begin with
//def  nodes = 5..204               // list of nodes, just one to begin with, we have 400 grids in an area of 10000.m x10000.m, grid size is 0.5.m x 0.5, so we have 400 grids with 400 sensors

def  nodes = 5..204
// generate random network geometry
def beacons = 2..4 // for only one gateway and 4 anchors
def gateways = 1..4 // for multiple gateways code
def nodeLocation = [:]
def gatewayDistance = [:]
def nodeGateway = [:]
def X=[]
def Y=[]
def Z=[]
def strx=0
def stry=0
def strz=0


//th problem from the distance and the number of nodes.. 100 nodes in 10000m will not work as nodes cant reach gateway,,,  225 in 4000. m distance didnt work eithor as there is a lot of collision
def D =  4000.m //10000.m  // side of the simulation area
def L =  400.m //1000.m  // distance between two node
double angleInDegree = 30
double angleInRadian = Math.toRadians(angleInDegree)

//nodeLocation[1] = [D/2, D/2 , -5.m] // the location of one gatways in one gateway experiments
// the folowwing locations are for multiple gateways experiments
nodeLocation[1] = [((D/2)-(1/3)*Math.cos(angleInRadian)*1000), D/2 -L/2, -5.m]
nodeLocation[2] = [((D/2)-(1/3)*Math.cos(angleInRadian)*1000), D/2 +L/2, -5.m]
nodeLocation[3] = [((D/2)+(2/3)*Math.cos(angleInRadian)*1000), D/2, -5.m]
nodeLocation[4] = [D/2, D/2, -500.m]



File filex = new File('.','Xlocations_200Nodes.txt')
File filey = new File('.','Ylocations_200Nodes.txt')
File filez = new File('.','Zlocations_200Nodes.txt')
File Gateway = new File('.','nodeGateway.txt')



/*
File filex = new File('.','xLocations.txt')
File filey = new File('.','yLocations.txt')
File filez = new File('.','zLocations.txt')

*/
    
/*
for(int c=5; c<=204; c++){
X[c] =rnd(0, D)
filex.append(Double.toString(X[c]))
filex<<"\n"
Y[c]= rnd(0, D)
filey.append(Double.toString(Y[c]))
filey<<"\n"
Z[c]= rnd(-480.m, -500.m)
filez.append(Double.toString(Z[c]))
filez<<"\n"
}
*/

/*
nodes.each { myAddr ->
 
  //nodeLocation[myAddr] = [rnd(-1.km, 1.km), rnd(-1.km, 1.km), rnd(-480.m, -500.m)]
  nodeLocation[myAddr] = [rnd(0, D), rnd(0, D), rnd(-480.m, -500.m)]
  //nodeLocation[myAddr] = [ 2500.m, 1800.m, -480.m]
}

*/


nodes.each { myAddr ->
 //nodeLocation[myAddr] = [rnd(0, D), rnd(0, D), rnd(-480.m, -500.m)]
 
  double locX= Double.parseDouble(filex.readLines().get(strx++))
  double locY= Double.parseDouble(filey.readLines().get(stry++))
  double locZ= Double.parseDouble(filez.readLines().get(strz++))
  //the following code is for multiple gateways to choose the closest gateway t the node
  nodeLocation[myAddr] = [locX, locY, locZ]
  for (addr in gateways){
  gatewayDistance.put(addr, distance(nodeLocation[myAddr], nodeLocation[addr]) )   
  println " distance between: "+myAddr+" and gateway: "+addr+" is: "+distance(nodeLocation[myAddr], nodeLocation[addr] )

  }
   println gatewayDistance.min { it.value }.key
   nodeGateway[myAddr] = gatewayDistance.min { it.value }.key
   //Gateway.append(Integer.toString( nodeGateway[myAddr])) // if i want to save the gatway for each node in a file
   //Gateway<<"\n"
}




def listSensorNodes = []
def listGatewayNodes = []
def pSensing
def nSlots 
def T
def totalSent = 0
def totalReceived = 0
def totalLossRate= 0
def totalTime = 0
def totalTxCountNs =0
def avgSent = 0
def avgReceived= 0
def avgLossRate=     0
def avgTime = 0 
def avgTxCountNs = 0
float loss
float Etotal =0
float Ptx=  0 //power consumed in transmission in watt
float Prx = 0 //power consumed in receiving packets in watt
float Etx = 0 // energy consumed in transmitiing 25% of packets in Joul
float energyAll =  0
def bytesDelivered =0
float JPerBit =0
def V=5, Itx = 0.3, Irx = 0.005 //from nanomodem datasheet 

def File fileTrace = new File('.','simulationFinalResults.txt')   
def File fileStats = new File('.','nodeStats.txt')  
def runs=[1, 1, 1]
def pSensingRange = [100, 100, 10]
//def nSlotsRange = [10, 100, 10]
def nSlotsRange = [50, 50, 10]
def gatewayDeamon 




for (pSensing = pSensingRange[0]; pSensing <= pSensingRange[1]; pSensing += pSensingRange[2]) {
  for (nSlots = nSlotsRange[0]; nSlots <= nSlotsRange[1]; nSlots += nSlotsRange[2]) {
    
   T = (int)Math.ceil(0.3675*nSlots).seconds   //simulation time in sec the duration of the data packet is 367.5 ms including the header
  //  T = 28000  //REQ time is 28380.6815 s
   
    
    fileTrace<<"\n\n"<<"Start scenario: P= "<<pSensing<<"  nSlots= "<<nSlots<<"  T= "<<T<<" s"<<"\n"
    fileTrace<<"-------------------------------------------------------------------------------------------------------------------------"<<"\n"<<"\n"
    fileTrace<<"p "<<"  "<<"nSlots "<<" "<<"TX Count"<<" "<<"RX Count"<<" "<<"Loss Count"<<"                     "<<"num of nodes sent 25%"<<"    "<<"25% received at (ms)"<<"\n"
    fileTrace<<"-------------------------------------------------------------------------------------------------------------------------"<<"\n"
    totalSent = 0
    totalReceived = 0
    totalLossRate= 0
    totalTime = 0
    totalTxCountNs =0
    
    for (def i= runs[0]; i <= runs[1]; i += runs[2]){
     def sumMsgSent = 0
     def sumMsgRec =0
     
      simulate T, {


          listSensorNodes.eachWithIndex { ii, index -> // `it` is the current element, while `i` is the index
               ii.noMessagesSent  =0
          }

          
         listGatewayNodes.eachWithIndex { iii, index -> // `it` is the current element, while `i` is the index
               iii.noMessagesReceived =0
          }

          
        beacons.each { myAddr ->
            def myNode = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
            stack: {  container ->  
              container.add 'anchor ', new USMARTAnchorDaemon()}
            )
        }   
   
        nodes.each { myAddr ->
            def sensorNode = new USMARTSensorDaemon( nodeGateway[myAddr], pSensing, nSlots)
            def myNode = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
            stack: {  container -> 
              container.add 'sense '+myAddr, sensorNode}
            )

          listSensorNodes.add(sensorNode)
        }


    /*
        def gateway = node '1', address: 1, location: nodeLocation[1], shell: true, stack: { container ->
            gatewayDeamon = new USMARTBaseAnchorDaemon(pSensing, nSlots) 
            container.add 'gateway', gatewayDeamon  
        }

        */
      
   // this is for multiple gateways scenario
       gateways.each { myAddr ->
            def gatewayNode = new USMARTBaseAnchorDaemon(pSensing, nSlots) 
            def gateway = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
            stack: {  container ->  
              container.add 'gateway ', gatewayNode}
            )
            listGatewayNodes.add(gatewayNode)
        }   

        
     }
    
      listSensorNodes.eachWithIndex { it, index -> // `it` is the current element, while `i` is the index
      sumMsgSent = sumMsgSent +  it.noMessagesSent 
     
         
    }

     listGatewayNodes.eachWithIndex { it, index -> // `it` is the current element, while `i` is the index
      sumMsgRec = sumMsgRec +  it.noMessagesReceived
     
         
    }


    
      loss = sumMsgSent ? 100*(sumMsgSent-sumMsgRec)/sumMsgSent : 0
     
     
      fileTrace <<pSensing<<"   "<<nSlots<<"       "<<sumMsgSent<<"        " <<sumMsgRec <<"       "<<loss<<"                    "<<"             "<<"\n" 
      
      totalSent = totalSent+sumMsgSent
      totalReceived = totalReceived+sumMsgRec
      totalLossRate= totalLossRate + loss
     // totalTime = totalTime+gatewayDeamon.time2
     // totalTxCountNs =  totalTxCountNs + gatewayDeamon.txCountNs
      
      fileStats.delete()
    }   
    fileTrace<<"-------------------------------------------------------------------------------------------------------------------------"<<"\n"
    fileTrace<<"End scenario: P= "<<pSensing<<"  nSlots= "<<nSlots<<"  T = "<<T/1000<<" s "<<totalSent<<"\n\n"
      
    avgSent = totalSent/runs[1]
    avgReceived=totalReceived/runs[1]
    avgLossRate= totalLossRate/runs[1]
   // avgTxCountNs = totalTxCountNs/runs[1]
   // avgTime = totalTime/runs[1]
    Ptx=  V*Itx //power consumed in transmission in watt
    Prx = V*Irx //power consumed in receiving packets in watt
    Etx = Math.floor(avgSent)*(Ptx*0.3675)
    energyAll =  (Math.floor(avgSent)*(Ptx*0.3675)) + (Math.floor(avgReceived)*(Prx*0.3675)) // total energy consumed for all the packets sent and received throughout the simulation
  //  EtxSubset = Math.floor(avgTxCountNs)*(Ptx*0.3675) // energy consumed in transmitiing 25% of packets in Joul
    bytesDelivered = Math.floor(avgReceived)* modem.frameLength[1]
    JPerBit = energyAll/(bytesDelivered * 8)
    /*
    if (avgTxCountNs > 0){
       Erx = Math.floor(0.25*200)*(Prx*0.3675) //energy consumed in receving 25% of packets in Joul
       Etotal =  EtxSubset+Erx  //Total consumed energy in Joul
       bytesDelivered = Math.floor(0.25*200)* modem.frameLength[1]
       JPerBit = Etotal/(bytesDelivered * 8)
       }

   */
    
    fileTrace<<"Average results of "<<runs[1]<<" experiments for scenario:  P= "<<pSensing<<"  nSlots= "<<nSlots<<"  T = "<<T<<" s "<<"\n"
    fileTrace<<"-------------------------------------------------------------------------------------------------------------------------"<<"\n"<<"\n"
    fileTrace<<"Num of packets sent= "<<"                                           "<<Math.floor(avgSent)<<"\n"
    fileTrace<<"Num of packets received= "<<"                                       "<<Math.floor(avgReceived)<<"\n"
    fileTrace<<"Packets loss rate= "<<"                                             "<<avgLossRate<<"\n"
    fileTrace<<"Energy consumed in transmitting = "<<"      "<<Etx<<" (J)"<<"\n"
    fileTrace<<"Total energy consumed for all packets sent and received = "<<"      "<<energyAll<<" (J)"<<"\n"
   // fileTrace<<"Num of nodes participated in sending 25% of packets is = "<<"       "<<Math.floor(avgTxCountNs)<<"\n"
   // fileTrace<<"25% of measurements received at = "<<"                              "<<avgTime<<" (ms) "<<"\n" 
    //fileTrace<<"Energy consumed in transmitting (only) 25% of msgs= "<<"      "<<EtxSubset<<" (J)"<<"\n"
    //fileTrace<<"Total energy consumed in sending and receiving 25% of msgs= "<<"    "<<Etotal<<" (J)"<<"\n"
    fileTrace<<"Energy per bit for received packets= "<<"                           "<<JPerBit<<" (J/bit)"<<"\n"
    
    
    
   
     
   // display statistics
  //float loss = trace.txCount ? 100*trace.dropCount/trace.txCount : 0
   println """TX Count \tRX Count \tLoss %\t\tnum of nodes sent 25% of mesurements\t\tTime(when gateway received first 25% of mesurements (ms)\tEnergy consumed in sending 25% of mesurements (J)
    --------\t--------\t------\t\t-----------------------------------------\t\t---------------------------------------------\t\t---------------------------------"""
 
  println sprintf('%7.3f\t\t%7.3f\t\t%5.1f\t\t\t\t\t%7.3f',
    [Math.floor(avgSent), Math.floor(avgReceived), avgLossRate, energyAll ])  
   



  }

}  






/*

println """SENSORS:"""

def noSensorsWithOneLocalisationHypothesis = 0
def noSensorsWithTwoLocalisationHypothesis = 0
def sumError = 0

listSensorNodes.eachWithIndex { it, i -> // `it` is the current element, while `i` is the index
    def locHypothesis = it.locHypothesis
    println "$i: $it no. loc hypothesis:${locHypothesis.size()} error:{$it.locError}"
    if (locHypothesis.size()==1) noSensorsWithOneLocalisationHypothesis++
    if (locHypothesis.size()==2) noSensorsWithTwoLocalisationHypothesis++
    if (locHypothesis.size()==1) sumError = sumError + it.locError
}

println """Localisation coverage is ${noSensorsWithOneLocalisationHypothesis/listSensorNodes.size()}  Avg. Error is ${sumError/noSensorsWithOneLocalisationHypothesis}"""

*/
