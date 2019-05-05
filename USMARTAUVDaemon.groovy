import org.arl.fjage.Message
import org.arl.unet.*
import java.net.ServerSocket

class USMARTAUVDaemon extends UnetAgent {

  final static int PING_PROTOCOL = Protocol.USER 
  def myAddress         // my node address
  Thread thread
  
  def x = 0.m
  def y = 0.m
  def z = -5.m

  def server 
   
  void startup() {
    def phy = agentForService Services.PHYSICAL
    subscribe topic(phy)    

    def server = new ServerSocket(4242)
 
    // Use as to treat closure as implementation for
    // the Runnable interface:
    def t = new Thread(
        { println 'hello' 
          //Thread.sleep(2000)
          //this.z = this.z-10
          while(true) {
            server.accept { socket ->
              println "processing new connection..."
                socket.withStreams { input, output ->
                  def reader = input.newReader()
                  def buffer = null
                  while ((buffer = reader.readLine()) != null) {
                    println "server received: $buffer"
                    if (buffer == "*bye*") {
                      println "exiting..."
                    }
                  }
              }
            }
          }
        }
        as Runnable)

    t.start()  // Output: hello

  }

  void processMessage(Message msg) {
    // get my node address from an agent providing the NODE_INFO service
    def nodeInfo = agentForService Services.NODE_INFO
    myAddress = nodeInfo.address
    println('location of AUV is ' + nodeInfo.location)
    nodeInfo.location = [0.m, 0.m, this.z]
   
   
    if (msg instanceof DatagramNtf && msg.protocol == PING_PROTOCOL) {
      println("processMessage by robot station node "+ myAddress+": from is " + msg.from)
      send new DatagramReq(recipient: msg.sender, to: 1, protocol: Protocol.DATA)
    }
  }
}

/*
 * 
Server code
import java.net.ServerSocket
def server = new ServerSocket(4242)

while(true) {
server.accept { socket ->
println "processing new connection..."
socket.withStreams { input, output ->
def reader = input.newReader()
def buffer = null
while ((buffer = reader.readLine()) != null) {
//def buffer = reader.readLine()
println "server received: $buffer"
if (buffer == "*bye*") {
println "exiting..."
System.exit(0)
} else {
output << "echo-response: " + buffer + "\n"
}
}
}
println "processing complete."
}
}
 */