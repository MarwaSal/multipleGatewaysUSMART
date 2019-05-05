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
n-node network with sensing daemons
--------------------------------

You can interact with all sensing nodes from the console shell. For example, try:
> sense 
> help sense

When you are done, exit the shell by pressing ^D or entering:
> shutdown
'''

///////////////////////////////////////////////////////////////////////////////
// simulator configuration

platform = RealTimePlatform

channel.model = ProtocolChannelModel
channel.soundSpeed = 1500.mps
channel.communicationRange = 6.km
channel.interferenceRange = 6.km

modem.dataRate = [100, 100].bps
modem.frameLength = [7, 7].bytes
modem.headerLength = 0
modem.preambleDuration = 0
modem.txDelay = 0
//modem.powerLevel   = [168.dB, 168.dB]

def nodes = 2..10                     // list of nodes

// generate random network geometry
def nodeLocation = [:]

nodeLocation[1] = [0.m, 0.m, -5.m]
nodes.each { myAddr ->
  //nodeLocation[myAddr] = [rnd(-1.km, 1.km), rnd(-1.km, 1.km), rnd(-480.m, -500.m)]
  nodeLocation[myAddr] = [rnd(-4.km, 4.km), rnd(-4.km, 4.km), rnd(-480.m, -500.m)]
}

nodeLocation[11] = [0.m, 0.m, -10.m]



// run simulation forever
simulate {
  node '1', address: 1, location: nodeLocation[1], shell: true, stack: { container ->
    container.add 'base', new USMARTBaseAnchorDaemon()
    //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
    container.shell.addInitrc "${script.parent}/fshrc.groovy"
  }
  //node '2', address: 2, location: [0.5.km, 0, 0], stack: { container ->
  //  container.add 'sense', new USMARTSensorDaemon()
  //}
  //node '3', address: 3, location: [0.5.km, 0, 0], stack: { container ->
  //  container.add 'sense', new USMARTSensorDaemon()
  //}

  node 'AUV-1',  address: 11, location: nodeLocation[1],  heading: 0.deg, mobility: true,
      stack: {  container -> 
          //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
          container.add 'sense', new USMARTAUVDaemon()
  }
     
  
  nodes.each { myAddr ->
      def myNode = node("${myAddr}", address: myAddr, location: nodeLocation[myAddr], 
        stack: {  container -> 
          //container.add 'mac', new org.arl.unet.mac.aloha.AlohaACS()
          container.add 'sense', new USMARTSensorDaemon()}
      )
  }
  
}

println """TX Count\tRX Count\tDrops \t\tLoss %\t\tOffered Load\tThroughput
--------\t--------\t------\t\t------------\t--------\t----------"""

// display statistics
  //float loss = trace.txCount ? 100*trace.dropCount/trace.txCount : 0
  float loss = trace.txCount ? 100*(trace.txCount-trace.rxCount)/trace.txCount : 0
  println sprintf('%6d\t\t%6d\t\t%6d\t\t%5.1f\t\t%7.3f\t\t%7.3f',
    [trace.txCount, trace.rxCount, trace.dropCount, loss, trace.offeredLoad, trace.throughput])