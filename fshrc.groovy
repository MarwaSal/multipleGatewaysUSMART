import org.arl.fjage.*
import org.arl.fjage.Message
import org.arl.fjage.Message.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.sim.*

// documentation for the 'ping' command
doc['ping'] = '''ping - Ping the sensors in the network

Examples:
  ping 2                // ping node address 2 
  ping 5,10             // ping node with address 5, 6 ...10
'''


// documentation for the 'ping' command
doc['sense'] = '''sense - Broadcast a sensing request to the network

Examples:
  sense               // broadcast sense request 
'''


doc['loc'] = '''loc - Broadcast a localisation request to the network

Examples:
  loc                 // localisation  request
'''

doc['exe'] = '''exe - Example generic message

Examples:
  exe                 // example generic  request
'''


//subscribe phy

// add a closure to define the 'ping' command
ping = { from_addr, to_addr=from_addr ->
    println "Requesting ping ..."
    def gateway = agentForService 'gateway'
    def req = new GenericMessage(agent('gateway'), Performative.REQUEST)
    req.type = 'ping'
    req.from_addr = from_addr
    req.to_addr = to_addr
    def rsp = gateway << req
    
}

// add a closure to define the 'sensr' command
sense = { ->
    // TO-DO call sense
    println "Broadcastig sensing request..."
    //phy << new DatagramReq(to: Address.BROADCAST, protocol: Protocol.DATA)
    def gateway = agentForService 'gateway'
    def req = new GenericMessage(agent('gateway'), Performative.REQUEST)
    req.type = 'sense'
    def rsp = gateway << req
}

// TODO csense

// add a closure to define the 'exe' command
exe = { ->
    println "Requesting generic message exe..."
    def gateway = agentForService 'gateway'
    def req = new GenericMessage(agent('gateway'), Performative.REQUEST)
    req.type = 'exe'
    def rsp = gateway << req
}

// add a closure to define the 'loc' command
loc = { ->
    println "Broadcasting localisation request..." //TO-DO: this should call the BaseDeamon
    def gateway = agentForService 'gateway'
    def req = new GenericMessage(agent('gateway'), Performative.REQUEST)
    req.type = 'loc'    
    def rsp = gateway << req
}
