package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    //private HashSet<SocketHandler> activeHandlersSet = new HashSet<SocketHandler>();
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<String, SocketHandler>();
    private ConcurrentHashMap<String, List<String>> roomsMap = new ConcurrentHashMap<String,List<String> >();

    /**
     * sendMessageToAll - Pole zprávu vem aktivním klientùm kromì sebe sama
     * 
     * @param sender  - reference odesílatele
     * @param message - øetìzec se zprávou
     */
    synchronized void sendMessageToAll(String name , String message) {
        for (Map.Entry<String, SocketHandler> entry : activeHandlersMap.entrySet()) // pro vechny aktivní handlery
            if (!entry.getValue().messages.offer("["+name+"] >> "+message)) // zkus pøidat zprávu do fronty jeho zpráv
                System.err.printf("Client %s message queue is full, dropping the message!\n", entry.getKey() );
    }
    synchronized boolean sentMessageToName(String sender, String message, String name) {
        if (activeHandlersMap.containsKey(name)) {
            if (!activeHandlersMap.get(name).messages.offer("["+sender+"] >> "+message)) {
                System.err.printf("Client %s message queue is full, dropping the message!\n", name);
                return false;
            }
            return true;
        }
        return false;
    }
    synchronized boolean sentMessageToRoom(String author, String message, String roomName) {
        if (roomsMap.containsKey(roomName)) {
            for (String name : roomsMap.get(roomName)) {
                if (!name.equals(author)){
                    sentMessageToName(author, message, name);
                }
            }
            return true;
        }
        return false;
    }
    synchronized List<String> getRoomList(String name) {
        List<String> roomList = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : roomsMap.entrySet()) {
            if (entry.getValue().contains(name)) {
                roomList.add(entry.getKey());
            }
        }
        return roomList;
    }
    synchronized void joinRoom(String roomName,String name) {
        if (roomsMap.containsKey(roomName)) {
            roomsMap.get(roomName).add(name);
        }
        else{
            roomsMap.put(roomName, new ArrayList<String>());
            roomsMap.get(roomName).add(name);
        }
    }
    synchronized boolean leaveRoom(String roomName,String name) {
        if (roomsMap.containsKey(roomName)) {
            roomsMap.get(roomName).remove(name);
            if (roomsMap.get(roomName).isEmpty()) {
                roomsMap.remove(roomName);
            }
            return true;
        }
        return false;
    }
    synchronized boolean setName(SocketHandler handler, String name) {
        if (activeHandlersMap.containsKey(name) || name.contains(" ")) {
            return false;
        }
        if (activeHandlersMap.containsKey(handlerToName(handler))) {
            for (Map.Entry<String, List<String>> entry : roomsMap.entrySet()) {
                if (entry.getValue().contains(handlerToName(handler))) {
                    entry.getValue().remove(handlerToName(handler));
                    entry.getValue().add(name);
                }
            }
            activeHandlersMap.remove(handlerToName(handler));
        }
        activeHandlersMap.put(name, handler);
        return true;
    }

    /**
     * add pøidá do mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pøidat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized boolean add(SocketHandler handler, String name) {
        if (activeHandlersMap.containsKey(name)|| name.contains(" ")) {
            return false;
        }
        else{
            activeHandlersMap.put(name, handler);
            return true;
        }
    }

    /**
     * remove odebere z mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized void remove(SocketHandler handler) {
        activeHandlersMap.remove(handlerToName(handler));
    }
    synchronized String handlerToName(SocketHandler handler) {
        for (Map.Entry<String, SocketHandler> entry : activeHandlersMap.entrySet()) {
            if (entry.getValue().equals(handler)) {
                return entry.getKey();
            }
        }
        return null;
    }
}

