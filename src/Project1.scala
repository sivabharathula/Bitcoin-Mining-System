import sun.security.jca.GetInstance
import akka.actor.ActorSystem
import java.util.UUID
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import akka.actor._
import akka.routing.RoundRobinRouter
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import java.security.MessageDigest


object ProjectFinal  {
  case class StartMining()
  case class ClientMine(targetZeroes: Int, startRange: Int, endRange: Int)
  case class StartServer()
  case class Start()
  case class End()
  case class RemoteMine()
  case class PingServer()
  case class Coins(coin: String)
  case class Result(nrOfCoins: Int, duration: Duration)

  def main(args: Array[String]) {
  class Client(ipaddr: String, nrOfWorkers: Int, listener: ActorRef) extends Actor {
    val starttime: Long = System.currentTimeMillis
    val remote = context.actorFor("akka.tcp://RemoteMinerSys@" + ipaddr + ":2250/user/Main_actor")
    var callBackcnt: Int = _
    var nrOfCoins: Int = _
    
       def receive = {
      case PingServer() =>
        remote ! RemoteMine()

      case ClientMine(targetZeroes, startRange, endRange) =>
        for (i <- 0 until nrOfWorkers)
        { println("Running Mining on client")
          context.actorOf(Props(new Worker(targetZeroes, (i+1)*startRange, (i+1)*endRange))) ! Start() }

      case Coins(str) =>
        nrOfCoins += 1
        println("Coin Mined at client: " )
        remote ! Coins(str)

      case End() =>
        callBackcnt += 1
        println(s"callBackcnt: $callBackcnt, nrOfWorkers: $nrOfWorkers")
        if (callBackcnt == nrOfWorkers) {
          println("Shutting down...")
          listener ! Result(nrOfCoins, duration = (System.currentTimeMillis - starttime).millis)
          remote ! End()
          context.stop(self)
        }

      case message: String =>
        println(s"Received message: '$message'")
    }
  }

  class Worker(targetZeroes: Int, startRange: Int, endRange: Int) extends Actor {
    val sha = MessageDigest.getInstance("SHA-256")
    def hex_digest(s: String): String = {
      sha.digest(s.getBytes)
        .foldLeft("")((s: String, b: Byte) => s +
          Character.forDigit((b & 0xf0) >> 4, 16) +
          Character.forDigit(b & 0x0f, 16))
    }

    def receive = {
      case Start() =>
        var continue = true
        var trails = startRange
        while (continue) {
          var strcmp = getNewString(trails)
          var hashval = hex_digest(strcmp.toString)
          if (isCoins(hashval, targetZeroes)) {
            sender ! Coins(strcmp)
            //continue = false
          }
          trails += 1
          if (trails == (endRange) )  {
              continue = false
            sender ! End()
          }
        }
    }
  }

  class Main_Actor(nrOfWorkers: Int, targetZeroes: Int, listener: ActorRef)
    extends Actor {

    var nrOfCoins: Int = _
    var callBackcnt: Int = _
    var load_dist: Int = _
    val starttime: Long = System.currentTimeMillis
    val InputRange = 1000000
    
    
    def receive = {
      case RemoteMine() =>
        sender ! ClientMine(targetZeroes, nrOfWorkers*load_dist*InputRange ,nrOfWorkers*(load_dist+1)*InputRange )
        load_dist += 1

      case StartMining() =>
        for (i <- 0 until nrOfWorkers) {
          context.actorOf(Props(new Worker(targetZeroes, load_dist*InputRange ,(load_dist+1)*InputRange))) ! Start()
          load_dist += 1
        }

      case Coins(str) =>
        nrOfCoins += 1
        val sha = MessageDigest.getInstance("SHA-256")
        def hex_digest(s: String): String = {
          sha.digest(s.getBytes)
            .foldLeft("")((s: String, b: Byte) => s +
              Character.forDigit((b & 0xf0) >> 4, 16) +
              Character.forDigit(b & 0x0f, 16))
        }
        println("%s\t%s".format(str, hex_digest(str)))

      case End() =>
        callBackcnt += 1
        if (nrOfCoins == 10) {
          // Send the result to the listener
          listener ! Result(nrOfCoins, duration = (System.currentTimeMillis - starttime).millis)
          
          context.stop(self)
        }
    }

  }

  class Listener extends Actor {
    def receive = {
      case Result(nrOfCoins, duration) =>
        println("\n\tCoins Count: \t\t%s\n\tTime Taken: \t%s"
          .format(nrOfCoins, duration))
        context.system.shutdown()
    }
  }

  def getNewString(numcnt: Int): String = {
    val gid = "sivabharathula"
    var strcnt: BigInt = numcnt
    var stringofa = strcnt.toString(36)
    var coin = gid.concat(stringofa)
    coin
  }

  def isCoins(str: String, targetZeroes: Int): Boolean = {
    def hashlimit(n: Int): String = {
      var A = ArrayBuffer[String]()
      var cnt: Int = 0
      while (cnt <= 63) {

        if (n == 0) A += "f"
        else A += "0"
        cnt = cnt + 1

      }
      if (n > 0) {
        A(n - 1) = "1"

      }
      A.mkString("")
    }

    val endval = hashlimit(targetZeroes)
    var result = (str < endval)
    return result
  }

 
    val serverConfig = ConfigFactory.parseString(
      """ 
      akka{ 
    		actor{ 
    			provider = "akka.remote.RemoteActorRefProvider" 
    		} 
    		remote{ 
                enabled-transports = ["akka.remote.netty.tcp"] 
            netty.tcp{ 
    			hostname = "10.136.47.160"
    			port = 2250
    		} 
      }      
    }""")

    val clientConfig = ConfigFactory.parseString(
      """akka{
		  		actor{
		  			provider = "akka.remote.RemoteActorRefProvider"
		  		}
		  		remote{
                   enabled-transports = ["akka.remote.netty.tcp"]
		  			netty.tcp{
						
						port = 0
					}
				}     
    	}""")
    val nrOfWorkers = 8

    if (!args(0).isEmpty()) {
      if (args(0).contains('.')) {
        val system = ActorSystem("ClientMiningSystem", ConfigFactory.load(clientConfig))

        
        val listener = system.actorOf(Props[Listener], name = "listener")

        val client = system.actorOf(Props(new Client(args(0), nrOfWorkers, listener)), name = "client") 
        client ! PingServer() 
      } else {
        val system = ActorSystem("RemoteMinerSys", ConfigFactory.load(serverConfig))

       
        val listener = system.actorOf(Props[Listener], name = "listener")

       
        val Main_actor = system.actorOf(Props(new Main_Actor(
          nrOfWorkers, args(0).toInt, listener)), name = "Main_actor")

        Main_actor ! StartMining()
      }
    }

  }
}

