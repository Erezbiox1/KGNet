@file:Suppress("unused", "PackageName")

package com.erezbiox1.gamecomm.Overlayed

import com.erezbiox1.gamecomm.Overlayed.KGNet.Player.ConsoleColors.RED
import com.erezbiox1.gamecomm.Overlayed.KGNet.Player.ConsoleColors.RESET
import com.erezbiox1.gamecomm.Server
import com.google.gson.GsonBuilder
import java.io.File
import kotlin.concurrent.thread

/**
 * Created by Erezbiox1 on 28/09/2018.
 * (C) 2018 Erez Rotem All Rights Reserved.
 * I am doing all of this in a single file so you can drag and drop this file and have a working server. This is for fun
 */

@Suppress("ClassName", "NullableBooleanElvis")
open class KGNet(port: Int) : Server(port){

    init {

        StorageManager.load()

    }

    // Session Control

    private object SessionManager {
        val usernamePlayerMap = mutableMapOf<String, Player>()
        val sessionMap = mutableMapOf<Player, Session>()

        // <editor-fold desc="Player Login">
        fun registerPlayer(username: String, password: String) : Player {
            val player = Players.storageHandler.createNew(username)

            // Add player to the player list, so he can be accessed by his username
            usernamePlayerMap[username] = player

            // Assigns the player the password
            player["password"] = password

            // Activates onPlayerRegister
            Events.execute(onPlayerRegister(player))

            return player
        }

        fun loginPlayer(player: Player, session: Session) {

            // Add the player to the online player list
            sessionMap.put(player, session)

            // Activates onPlayerLogin
            Events.execute(onPlayerLogin(player))

        }

        fun containsUser(username: String) : Boolean {
            return Players.storageHandler.getObject(username) != null
        }

        fun checkPassword(username: String, password: String) : Player? {
            val player = usernamePlayerMap[username] ?: Players.storageHandler.getObject(username)
            return if(player?.get<String>("password") == password) player else null
        }

        fun disconnectPlayer(player: Player) {

            // Removes the player to the online player list
            sessionMap.remove(player)

            // Activates onPlayerDisconnect
            Events.execute(onPlayerDisconnect(player))

        }
        // </editor-fold>
    }

    // Server Network Communications

    override fun onOpen(session: Session) {
        thread {
            Thread.sleep(100) // You need to wait for the client to fully connect, lazy approach

            ServerQuestion("Please enter your username:", session) { username ->
                // Does the player already exists?
                if(SessionManager.containsUser(username)){
                    val passQuestion = ServerQuestion("Please enter your password:", session) { password ->
                        val player = SessionManager.checkPassword(username, password)

                        if (player != null) {
                            // Adds the player to the player's session so it can be access when he send a message
                            session["player"] = player

                            // Finally login the player
                            SessionManager.loginPlayer(player, session)
                        } else {
                            session.sendMessage("Incorrect password.")
                        }
                    }
                } else {
                    ServerQuestion("That player doesn't exist. To register please enter \"$username\" new password.", session){
                        password ->
                        // Registers the player
                        val player = SessionManager.registerPlayer(username, password)

                        // Adds the player to the player's session so it can be access when he send a message
                        session["player"] = player

                        // Finally login the player
                        SessionManager.loginPlayer(player, session)
                    }
                }
            }
        }
    }

    override fun onMessage(session: Session, message: String) {

        // Is this session logged in?
        val player: Player? = session["player"]
        player?.let {
            if(message.startsWith("/")){
                Commands.handleCommand(it, message.removeRange(0, 1))
            } else {
                Events.execute(onMessage(player, message))
            }
        }

        // If not, is this answering a question? ( yes. )
        val question: ServerQuestion? = session["question"]
        question?.let {
            question.answer(message)
        }

    }

    override fun onClose(session: Session) {
        val player: Player? = session["player"]
        player?.let {
            // Remove his session
            session["player"] = ""

            // Disconnect the player ( trigger events )
            SessionManager.disconnectPlayer(player)
        }
    }

    // Player

    object Players {

        internal val storageHandler = object : StorageManager.StorageHandler<Player>("Players") {
            override fun saveAll() {
                SessionManager.usernamePlayerMap.forEach { username, player ->
                    updateObject(username, player)
                }
            }

            override fun update(objectName: String, obj: Player) : MutableMap<String, Any> {
                return obj.props
            }

            override fun create(objectName: String, props: MutableMap<String, Any>) : Player {
                val player = Player(objectName, props)
                SessionManager.usernamePlayerMap.put(objectName, player)
                return player
            }

            override fun createNew(objectName: String) : Player {
                return Player(objectName, mutableMapOf())
            }
        }

        val onlinePlayers
            get() = SessionManager.sessionMap.keys.toList()

        fun announce(message: String){
            onlinePlayers.forEach {
                it.sendMessage("$message")
            }
        }

        fun getPlayer(username: String) : Player? {
            return SessionManager.usernamePlayerMap[username] ?: storageHandler.getObject(username)
        }

    }

    class Player internal constructor(val username: String, val props: MutableMap<String, Any>){
        private val session: Session
            get() = SessionManager.sessionMap[this]!!

        init {
            if(props.isEmpty()){
                this["op"] = false
            }
        }

        var admin: Boolean
            get() = this["op"]!!
            set(boolean) { this["op"] = boolean }

        fun sendMessage(message: String) {
            session.sendMessage(message)
        }

        // <editor-fold desc="Player Props Control">

        fun has(key: String) : Boolean {
            return props.containsKey(key)
        }

        inline operator fun <reified T> get(key: String) : T? {
            val obj = props[key]
            return when {
                obj == null                 -> null
                obj is T                    -> obj
                T::class == Int::class      -> (obj as Double).toInt() as T
                T::class == String::class   -> obj.toString() as T
                else                        -> null
            }
        }

        operator fun set(key: String, value: Any) {
            props[key] = value
        }

        // </editor-fold>

        override fun toString(): String {
            return username
        }

        object ConsoleColors {
            val RESET = "\u001B[0m"
            val RED = "\u001B[31m"
        }
    }

    // Commands

    object Commands {
        private val commandListeners = mutableListOf<CommandListener>()

        fun handleCommand(player: Player, command: String){
            Events.execute(onCommand(player, command))
            val split = command.split(" ")
            commandListeners.filter { it.name.toLowerCase() == split[0].toLowerCase() }.forEach {
                if(it.function(CommandExecution(player, command, split)))
                    return@forEach
            }
        }

        fun command(prefixedName: String, function: (CommandExecution) -> Unit) {
            val adminRequired = prefixedName.startsWith("_")
            val name = if(adminRequired) prefixedName.removeRange(0, 1) else prefixedName

            val callback: (CommandExecution) -> Boolean = {
                try {
                    if(adminRequired && !it.player.admin)
                        error("Insufficient Permission")
                    function(it); true
                }catch (e: IllegalStateException){
                    val message = if(e.message.isNullOrEmpty()) "Invalid command parameters" else e.message
                    it.player.sendMessage("${RED}Error!$RESET $message.")
                    false
                }catch (e: Exception){
                    e.printStackTrace()
                    false
                }
            }
            commandListeners.add(CommandListener(name, callback))
        }

        class CommandExecution(val player: Player, val command: String, val args: List<String>)
        class CommandListener(val name: String, val function: (CommandExecution) -> Boolean)
    }

    // Event + Events

    object Events {
        private val eventListeners = mutableListOf<EventListener<Event>>()

        fun <T : Event> listen(function: (T) -> Any){
            val callback: (T) -> Boolean = {
                function(it) as? Boolean ?: false
            }
            eventListeners.add(EventListener(callback) as EventListener<Event>)
        }

        fun execute(event: Event) {
            eventListeners.forEach {
                if(it.run(event))
                    return@forEach
            }
        }

        class EventListener<in T : Event>(private val callback: (T) -> Boolean){
            fun run(event: Event) : Boolean {
                return try {
                    (event as? T)?.let(callback) == true
                }catch (e: ClassCastException){
                    // This project is for fun, I don't got time to mess with kotlin screwed generics.
                    false
                }
            }

        }
    }

    open class Event

    class onMessage             (val player: Player, val message: String)   : Event()
    class onCommand             (val player: Player, val message: String)   : Event()
    class onPlayerLogin         (val player: Player)                        : Event()
    class onPlayerRegister      (val player: Player)                        : Event()
    class onPlayerDisconnect    (val player: Player)                        : Event()


    // ServerQuestion, acts and menus

    private class ServerQuestion(val question: String, val session: Session, val function: (String) -> Unit){
        init {
            ask()
        }

        fun ask(){
            session["question"] = this
            session.sendMessage(question)
        }

        fun answer(message: String){
            if(!message.isBlank()) {
                session["question"] = ""
                function(message)
            }
        }
    }

    //class Question(val question: String, val player: Player, val function: (String) -> Unit)

    // Storage

    object StorageManager {

        val saveFile = File("save.txt")

        private var storageMap: MutableMap<String, MutableMap<String, Any>> = mutableMapOf()
        private val handlers = mutableMapOf<String, StorageHandler<Any>>()

        private fun createObject(typeName: String, objectName: String, props: MutableMap<String, Any> = mutableMapOf()) : Any? {
            val handler = handlers[typeName]
            handler?.let {
                val obj = it.create(objectName, props)
                updateObject(it.name, objectName, obj)
                return obj
            }
            return null
        }

        private fun updateObject(typeName: String, objectName: String, obj: Any){
            val handler = handlers[typeName]
            handler?.let {
                val map = storageMap.getOrPut(typeName, { mutableMapOf() })
                map[objectName] = handler.update(objectName, obj)
            }
        }

        private fun loadObject(typeName: String, objectName: String) : Any? {
            handlers[typeName]?.let {
                handler ->
                storageMap[typeName]?.let {
                    map ->
                    map[objectName]?.let {
                        return handler.create(objectName, it as MutableMap<String, Any>)
                    }
                }
            }
            return null
        }

        private fun updateAll(){
            handlers.values.forEach {
                it.saveAll()
            }
        }

        abstract class StorageHandler<T>(val name: String){
            abstract fun saveAll()
            abstract fun update(objectName: String, obj: T) : MutableMap<String, Any>
            abstract fun create(objectName: String, props: MutableMap<String, Any>) : T
            abstract fun createNew(objectName: String) : T

            fun createObject(objectName: String, props: MutableMap<String, Any> = mutableMapOf()) : T? {
                return createObject(name, objectName, props) as? T
            }

            fun getObject(objectName: String) : T? {
                return loadObject(name, objectName) as? T
            }

            fun updateObject(objectName: String, obj: T){
                StorageManager.updateObject(name, objectName, obj as Any)
            }

            init {
                handlers.put(name, this as StorageHandler<Any>)
            }
        }

        fun save(){
            updateAll()
            val gson = GsonBuilder().create()
            saveFile.writeText(gson.toJson(storageMap))
        }

        fun load(){
            if(!saveFile.exists() || saveFile.isDirectory)
                saveFile.createNewFile()
            else {
                val gson = GsonBuilder().create()
                gson.fromJson(saveFile.readText(), storageMap::class.java)?.let {
                    storageMap = it
                }
            }
        }
    }

}