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
import java.net.ServerSocket

///////////////////////////////////////////////////////////////////////////////
// display documentation

println '''
n-node network with sensing and localisation daemons
--------------------------------

You can interact with all sensing nodes from the console shell. For example, try:
> ping
> sense
> loc 
> help ping
> help sense
> help loc
When you are done, exit the shell by pressing ^D or entering:
> shutdown
'''

///////////////////////////////////////////////////////////////////////////////
// simulator configuration

platform = RealTimePlatform

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

modem.dataRate = [40, 40].bps
modem.frameLength = [14, 14].bytes // was 7
//modem.frameLength = [7, 7].bytes // was 7
modem.headerLength = 0
modem.preambleDuration = 0
modem.txDelay = 0
modem.powerLevel   = [168.dB, 168.dB]

//def nodes = 5..5
//def nodes = 5..10                     // list of nodes, just one to begin with
def nodes = 5..104                     // list of nodes, just one to begin with

// generate random network geometry
def beacons = 2..4
def nodeLocation = [:]

def D = 4000.m  // side of the simulation area
def L = 400.m  // distance between two node

//anchors = [ (D/2 - L, D/2 - L, 0)  
//          , (D/2 + L, D/2 - L, 0)
//          , (D/2,     D/2 + L, 0)
//          , (D/2, D/2, -500)
          
nodeLocation[1] = [D/2-L, D/2 -L, -5.m]
nodeLocation[2] = [D/2+L, D/2 -L, -5.m]
nodeLocation[3] = [D/2, D/2+L, -5.m]
nodeLocation[4] = [D/2, D/2, -500.m]

nodes.each { myAddr ->
  //nodeLocation[myAddr] = [rnd(-1.km, 1.km), rnd(-1.km, 1.km), rnd(-480.m, -500.m)]
  nodeLocation[myAddr] = [rnd(0, D), rnd(0, D), rnd(-480.m, -500.m)]
  //nodeLocation[myAddr] = [ 2500.m, 1800.m, -480.m]
}

def listSensorNodes = []

def T = 7.5.hours
// run simulation forever
simulate {

  node '1', address: 1, location: nodeLocation[1], shell: true, stack: { container ->
    container.add 'gateway', new USMARTBaseAnchorDaemon()
    //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
    container.shell.addInitrc "${script.parent}/fshrc.groovy"
  }
  
  beacons.each { myAddr ->
      def myNode = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
        stack: {  container -> 
          //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
          container.add 'anchor', new USMARTAnchorDaemon()}
      )
  }   

  nodes.each { myAddr ->
      def sensorNode = new USMARTSensorDaemon()
      def myNode = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
        stack: {  container -> 
          //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
          container.add 'sense'+myAddr, sensorNode}
      )

      listSensorNodes.add(sensorNode)
  }

 
  
}

println """TX Count\tRX Count\tDrops \t\tLoss %\t\tOffered Load\tThroughput
--------\t--------\t------\t\t------------\t--------\t----------"""

// display statistics
  //float loss = trace.txCount ? 100*trace.dropCount/trace.txCount : 0
  float loss = trace.txCount ? 100*(trace.txCount-trace.rxCount)/trace.txCount : 0
  println sprintf('%6d\t\t%6d\t\t%6d\t\t%5.1f\t\t%7.3f\t\t%7.3f',
    [trace.txCount, trace.rxCount, trace.dropCount, loss, trace.offeredLoad, trace.throughput])

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

