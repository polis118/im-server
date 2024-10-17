package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class App {

	public static void main(String[] args) {
		int port = 33000, max_conn = 2;

		if (args.length > 0) {
			if (args[0].startsWith("--help")) {
				System.out.printf("Usage: Server [PORT] [MAX_CONNECTIONS]\n" +
						"If PORT is not specified, default port %d is used\n" +
						"If MAX_CONNECTIONS is not specified, default number=%d is used", port, max_conn);
				return;
			}
			try {
				port = Integer.decode(args[0]);
			} catch (NumberFormatException e) {
				System.err.printf("Argument %s is not integer, using default value", args[0], port);
			}
			if (args.length > 1)
				try {
					max_conn = Integer.decode(args[1]);
				} catch (NumberFormatException e) {
					System.err.printf("Argument %s is not integer, using default value", args[1], max_conn);
				}

		}
		// TODO Auto-generated method stub
		System.out.printf("IM server listening on port %d, maximum nr. of connections=%d...\n", port, max_conn);
		ExecutorService pool = Executors.newFixedThreadPool(2 * max_conn);
		ActiveHandlers activeHandlers = new ActiveHandlers();

		try {
			ServerSocket sSocket = new ServerSocket(port);
			do {
				Socket clientSocket = sSocket.accept();
				clientSocket.setKeepAlive(true);
				SocketHandler handler = new SocketHandler(clientSocket, activeHandlers);
				pool.execute(handler.inputHandler);
				pool.execute(handler.outputHandler);
			} while (!pool.isTerminated());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			pool.shutdown();
			try {
				// Wait a while for existing tasks to terminate
				if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					pool.shutdownNow(); // Cancel currently executing tasks
					// Wait a while for tasks to respond to being cancelled
					if (!pool.awaitTermination(60, TimeUnit.SECONDS))
						System.err.println("Pool did not terminate");
				}
			} catch (InterruptedException ie) {
				// (Re-)Cancel if current thread also interrupted
				pool.shutdownNow();
				// Preserve interrupt status
				Thread.currentThread().interrupt();
			}
		}
	}
}
