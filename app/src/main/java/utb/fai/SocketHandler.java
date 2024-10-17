package utb.fai;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;

public class SocketHandler {
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientID;
	String name;

	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers;

	/**
	 * messages je fronta pøíchozích zpráv, kterou musí mít kaý klient svoji
	 * vlastní - pokud bude je pøetíená nebo nefunkèní klientova sí,
	 * èekají zprávy na doruèení právì ve frontì messages
	 */
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
	}

	class OutputHandler implements Runnable {
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				//writer.write("\nYou are connected from " + clientID + "\n");
				writer.flush();
				while (!inputFinished) {
					String m = messages.take();// blokující čtení - pokud není ve frontě zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");

		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				String request = "";
				/**
				 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
				 * vech aktivních handlerù, aby chodily zprávy od ostatních i nám
				 */
				//activeHandlers.add(SocketHandler.this);
				BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				PrintWriter writer = new PrintWriter(mySocket.getOutputStream(), true);
				writer.println("Please input your name (name cannot contain space): ");
				while((request = reader.readLine()) != null){

					if(activeHandlers.add(SocketHandler.this,request)){
						//System.out.println("Client " + clientID + " set name to " + request);
						writer.println("Name set to: " + request);
						break;
					}
					else{
						writer.println("Non valid name entered. Name is already taken or contains forbiden character(space). Please input another name: ");
					}
				}
				name = activeHandlers.handlerToName(SocketHandler.this);
				activeHandlers.joinRoom("public", name);
				while ((request = reader.readLine()) != null) { // pøila od mého klienta nìjaká zpráva?
					if(request.startsWith("#setMyName ")){
						if(activeHandlers.setName(SocketHandler.this,request.substring("#setMyName ".length()))){
							name = request.substring("#setMyName ".length());
							System.out.println("Client " + clientID + " set name to " + name);
							activeHandlers.sendMessageToAll(name, "Client " + clientID + " set name to " + name);
						}
						else{
							writer.println("Non valid name entered. Name is already taken or contains forbiden character(space).");
						}

					}
					else if(request.startsWith("#sendPrivate ")){
						String[] parts = request.split(" ", 3);
						if(parts.length == 3){
							if(!activeHandlers.sentMessageToName(name,parts[2],parts[1])){
								writer.println("Client " + parts[1] + " is not connected or his message queue is full.");
							}
						}
						else{
							writer.println("Invalid command. Usage: #sendPrivate <name> <message>");
						}

					}
					else if(request.startsWith("#join ")){
						activeHandlers.joinRoom(request.substring("#join ".length()),name);
						writer.println("Joined room " + request.substring("#join ".length()));
					}
					else if(request.startsWith("#leave ")){
						if(activeHandlers.leaveRoom(request.substring("#leave ".length()),name)){
							writer.println("Left room " + request.substring("#leave ".length()));
						}
						else{
							writer.println("You are not in room " + request.substring("#leave ".length()));
						}
					}
					else if(request.startsWith("#groups")){
						List<String> groups = activeHandlers.getRoomList(name);
						if(groups.size() == 1){
							writer.println(groups.get(0));
						}
						else{
							writer.println(String.join(",", groups));
						}

					}
					else{
						List<String> groups = activeHandlers.getRoomList(name);
						for(String group : groups){
							activeHandlers.sentMessageToRoom(name,request,group);
						}
					}
				}
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// remove yourself from the set of activeHandlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}

	}

}
