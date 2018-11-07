import com.erezbiox1.gamecomm.Overlayed.KGNet
import com.erezbiox1.gamecomm.Overlayed.KGNet.*

/**
 * Created by Erezbiox1 on 29/09/2018.
 * (C) 2018 Erez Rotem All Rights Reserved.
 */

fun main(args: Array<String>){

    println("Initiating Server Test...")

    val server = KGNet(6070)

    println("Registering Events...")

    Events.listen<onPlayerRegister> {
        it.player["balance"] = 1000.0

        println("${it.player.username} has registered to the server!")
    }

    Events.listen<onPlayerLogin>{
        broadcast("${it.player.username} has logged on to the server!")
    }

    Events.listen<onPlayerDisconnect> {
        broadcast("${it.player.username} has logged off the server!")
    }

    Events.listen<onMessage> {
        broadcast("${it.player}: ${it.message}")
    }

    Events.listen<onCommand> {
        println("${it.player} executed /${it.message}")
    }

    println("Registering Commands...")

    Commands.command("bal") {
        val bal: Int = it.player.get<Int>("balance") ?: 0
        it.player.sendMessage("Your balance: $bal$")
    }

    Commands.command("pay") {
        if(it.args.size != 3)
            error("")

        val bal: Int = it.player["balance"]!!
        val target = Players.getPlayer(it.args[1]) ?: error("Requested player can't be found")
        val amount = it.args[2].toIntOrNull() ?: error("Invalid amount")

        if(amount > bal)
            error("Insufficient Funds")

        it.player["balance"] = bal - amount
        target["balance"] = target.get<Int>("balance")!! + amount

        it.player.sendMessage("Success! You payed $target $amount$")
        target.sendMessage("Received $amount$ from ${it.player}")
    }

    Commands.command("_setBal"){
        if(it.args.size != 3)
            error("")

        val target = Players.getPlayer(it.args[1]) ?: error("Requested player can't be found")
        val targetBal: Int = target["balance"]!!

        val amountString = it.args[2]
        when {
            amountString.startsWith("+") -> {
                val amount = amountString.removeRange(0, 1).toIntOrNull() ?: error("Invalid amount")
                target["balance"] = targetBal + amount
                it.player.sendMessage("Success! Added $amount$ to $target")
            }
            amountString.startsWith("-") -> {
                val amount = amountString.removeRange(0, 1).toIntOrNull() ?: error("Invalid amount")
                target["balance"] = targetBal - amount
                it.player.sendMessage("Success! Removed $amount$ from $target")
            }
            amountString.toIntOrNull() != null -> {
                target["balance"] = amountString.toInt()
                it.player.sendMessage("Success! Set $target's balance to $amountString$")
            }
            else -> error("Invalid amount")
        }

    }

    Commands.command("_save"){
        StorageManager.save()
        it.player.sendMessage("Saved Successfully!")
    }

    println("Booting up...")

    server.start()

    println("Done! Running...")

}

fun broadcast(message: String){
    println(message)
    Players.announce(message)
}