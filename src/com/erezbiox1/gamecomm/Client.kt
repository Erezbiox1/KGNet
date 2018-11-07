package com.erezbiox1.gamecomm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ConnectException
import java.net.Socket

/**
 * Created by Erezbiox1 on 18/09/2018.
 * (C) 2018 Erez Rotem All Rights Reserved.
 */
class Client(private val serverName: String, private val port: Int) : Thread() {

    private val socket = Socket(serverName, port)
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    private var listener: ((String) -> Unit) = {}

    init {
        start()
    }

    override fun run() {
        while(!socket.isClosed){
            try {
                val message = input.readUTF()
                listener(message)
            }catch (e: EOFException) {
                System.err.println("You were disconnected from the server.")
                break
            }catch (e: ConnectException) {
                System.err.println("Cannot connect to the server.")
                break
            }catch (e: Exception){
                if(e.message?.contains("reset") == true) {
                    System.err.println("Server closed")
                    break
                }
                println("error")
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(message: String){
        output.writeUTF(message)
        output.flush()
    }

    fun listen(block: ((String) -> Unit)){
        listener = block
    }

}