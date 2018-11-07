package com.erezbiox1.gamecomm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket


/**
 * Created by Erezbiox1 on 18/09/2018.
 * (C) 2018 Erez Rotem All Rights Reserved.
 */

open class Server(port: Int) {

    private val serverSock = ServerSocket(port)
    private val clientPool = mutableListOf<Session>()
    private val serverThread = ServerThread()
    private var running = true

    open fun start(){
        serverSock.soTimeout = 0
        serverThread.start()
    }

    open fun stop(){
        running = false
    }

    open fun onOpen(session: Session){}
    open fun onMessage(session: Session, message: String){}
    open fun onClose(session: Session){}

    inner class ServerThread : Thread() {
        override fun run() {
            while(running){
                val client = this@Server.serverSock.accept()
                this@Server.clientPool.add(Session(client))
            }
        }
    }

    inner class Session(private val link: Socket, val props: MutableMap<String, Any> = mutableMapOf<String, Any>()) : Thread() {

        private val input = DataInputStream(link.getInputStream())
        private val output = DataOutputStream(link.getOutputStream())

        init {
            start()
        }

        override fun run() {
            this@Server.onOpen(this)

            while (!link.isClosed) {
                try {
                    val message = input.readUTF()
                    this@Server.onMessage(this, message)
                }catch (e: Exception){
                    if(e.message?.contains("reset") == true)
                        break
                }
            }

            this@Server.onClose(this)
            this@Server.clientPool.remove(this)
        }

        fun sendMessage(message: String){
            output.writeUTF(message)
            output.flush()
        }

        fun getIP() : String {
            return link.remoteSocketAddress.toString()
        }

        fun has(key: String) : Boolean {
            return props.containsKey(key)
        }

        fun disconnect(){
            link.close()
        }

        inline operator fun <reified T> get(key: String) : T? {
            val obj = props[key]
            return if(obj != null && obj is T)
                obj
            else
                null
        }

        operator fun set(key: String, value: Any) {
            props[key] = value
        }
    }

}