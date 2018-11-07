import com.erezbiox1.gamecomm.Client

/**
 * Created by Erezbiox1 on 20/09/2018.
 * (C) 2018 Erez Rotem All Rights Reserved.
 */

fun main(args: Array<String>){
    val client = Client("localhost", 6070)
    client.listen {
        //println("<- $it")
        println(it)
    }

    while(true){
        //print("-> ")
        client.sendMessage(readLine()!!)
    }
}