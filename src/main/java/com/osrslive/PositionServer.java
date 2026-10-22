package com.osrslive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Minimale WebSocket-server die alleen op 127.0.0.1 luistert en tekstberichten naar de app stuurt.
 * Geen externe bibliotheken. Alleen verbindingen van deze computer en van de OSRS Companion-app
 * (Origin "null" of "file://") worden geaccepteerd.
 */
public class PositionServer
{
	private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	private static final Set<String> HOSTS = Set.of("127.0.0.1", "localhost");

	private final int port;
	private final List<Client> clients = new CopyOnWriteArrayList<>();
	private volatile ServerSocket server;
	private volatile boolean running;
	private volatile String lastMessage;
	/** Eén aparte draad voor het versturen, zodat de speldraad nooit op het netwerk wacht. */
	private final ExecutorService sender = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "osrs-live-positie-send");
		t.setDaemon(true);
		return t;
	});

	public PositionServer(int port)
	{
		this.port = port;
	}

	public int getPort()
	{
		return server == null ? port : server.getLocalPort();
	}

	public void start() throws IOException
	{
		server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
		running = true;
		Thread t = new Thread(this::acceptLoop, "osrs-live-positie-accept");
		t.setDaemon(true);
		t.start();
	}

	public void stop()
	{
		running = false;
		try
		{
			if (server != null)
			{
				server.close();
			}
		}
		catch (IOException ignored)
		{
		}
		for (Client c : clients)
		{
			c.close();
		}
		clients.clear();
		sender.shutdownNow();
	}

	/** Stuurt een bericht naar alle verbonden apps. Een positie wordt bewaard voor nieuwe verbindingen. */
	public void broadcast(String json, boolean remember)
	{
		if (remember)
		{
			lastMessage = json;
		}
		try
		{
			sender.execute(() ->
			{
				for (Client c : clients)
				{
					c.send(json);
				}
			});
		}
		catch (RejectedExecutionException ignored)
		{
		}
	}

	public void forget()
	{
		lastMessage = null;
	}

	private void acceptLoop()
	{
		while (running)
		{
			try
			{
				Socket s = server.accept();
				Thread t = new Thread(() -> handle(s), "osrs-live-positie-client");
				t.setDaemon(true);
				t.start();
			}
			catch (IOException e)
			{
				if (!running)
				{
					return;
				}
			}
		}
	}

	static boolean originAllowed(String origin)
	{
		if (origin == null || origin.isEmpty() || origin.equals("null") || origin.equals("file://"))
		{
			return true;
		}
		String o = origin.toLowerCase(Locale.ROOT);
		return o.equals("http://localhost") || o.startsWith("http://localhost:")
			|| o.equals("http://127.0.0.1") || o.startsWith("http://127.0.0.1:");
	}

	private void handle(Socket s)
	{
		Client c = null;
		try
		{
			s.setSoTimeout(10000);
			s.setTcpNoDelay(true);
			InputStream in = new BufferedInputStream(s.getInputStream());
			String head = readHead(in);
			if (head == null)
			{
				s.close();
				return;
			}
			String key = null, origin = null, host = null, upgrade = null;
			for (String line : head.split("\r\n"))
			{
				int i = line.indexOf(':');
				if (i < 0)
				{
					continue;
				}
				String k = line.substring(0, i).trim().toLowerCase(Locale.ROOT), v = line.substring(i + 1).trim();
				if (k.equals("sec-websocket-key"))
				{
					key = v;
				}
				else if (k.equals("origin"))
				{
					origin = v;
				}
				else if (k.equals("host"))
				{
					host = v;
				}
				else if (k.equals("upgrade"))
				{
					upgrade = v;
				}
			}
			String hostName = host == null ? "" : host.replaceAll(":\\d+$", "").toLowerCase(Locale.ROOT);
			if (key == null || upgrade == null || !upgrade.equalsIgnoreCase("websocket")
				|| !HOSTS.contains(hostName) || !originAllowed(origin))
			{
				s.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				s.close();
				return;
			}
			String accept = Base64.getEncoder().encodeToString(
				MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
			OutputStream out = s.getOutputStream();
			out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
				+ accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			s.setSoTimeout(0);
			c = new Client(s, out);
			clients.add(c);
			c.send("{\"type\":\"welcome\",\"version\":\"1.0.0\"}");
			String last = lastMessage;
			if (last != null)
			{
				c.send(last);
			}
			readFrames(in, c);
		}
		catch (Exception ignored)
		{
		}
		finally
		{
			if (c != null)
			{
				clients.remove(c);
				c.close();
			}
			else
			{
				try
				{
					s.close();
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private static String readHead(InputStream in) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		int prev = 0;
		while (sb.length() < 8192)
		{
			int b = in.read();
			if (b < 0)
			{
				return null;
			}
			sb.append((char) b);
			int n = sb.length();
			if (n >= 4 && sb.charAt(n - 1) == '\n' && sb.charAt(n - 2) == '\r' && sb.charAt(n - 3) == '\n' && sb.charAt(n - 4) == '\r')
			{
				return sb.toString();
			}
		}
		return null;
	}

	/** Leest binnenkomende frames: alleen ping en sluiten worden beantwoord, de rest wordt genegeerd. */
	private static void readFrames(InputStream in, Client c) throws IOException
	{
		while (true)
		{
			int b0 = in.read();
			if (b0 < 0)
			{
				return;
			}
			int b1 = in.read();
			if (b1 < 0)
			{
				return;
			}
			int op = b0 & 0x0f;
			boolean masked = (b1 & 0x80) != 0;
			long len = b1 & 0x7f;
			if (len == 126)
			{
				len = (in.read() << 8) | in.read();
			}
			else if (len == 127)
			{
				len = 0;
				for (int i = 0; i < 8; i++)
				{
					len = (len << 8) | in.read();
				}
			}
			if (len > 1 << 16)
			{
				return;
			}
			byte[] mask = new byte[4];
			if (masked)
			{
				in.readNBytes(mask, 0, 4);
			}
			byte[] data = in.readNBytes((int) len);
			if (data.length < len)
			{
				return;
			}
			if (masked)
			{
				for (int i = 0; i < data.length; i++)
				{
					data[i] ^= mask[i & 3];
				}
			}
			if (op == 8)
			{
				return;
			}
			if (op == 9)
			{
				c.frame(10, data);
			}
		}
	}

	private static class Client
	{
		private final Socket socket;
		private final OutputStream out;

		Client(Socket socket, OutputStream out)
		{
			this.socket = socket;
			this.out = out;
		}

		void send(String text)
		{
			frame(1, text.getBytes(StandardCharsets.UTF_8));
		}

		synchronized void frame(int op, byte[] p)
		{
			try
			{
				int n = p.length;
				if (n < 126)
				{
					out.write(new byte[]{(byte) (0x80 | op), (byte) n});
				}
				else if (n < 65536)
				{
					out.write(new byte[]{(byte) (0x80 | op), 126, (byte) (n >> 8), (byte) n});
				}
				else
				{
					return;
				}
				out.write(p);
				out.flush();
			}
			catch (IOException e)
			{
				close();
			}
		}

		void close()
		{
			try
			{
				socket.close();
			}
			catch (IOException ignored)
			{
			}
		}
	}
}
