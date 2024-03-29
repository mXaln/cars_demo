
package org.wycliffeassociates.demo;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.ServerDiscoveryHandler;
import com.esotericsoftware.minlog.Log;
import org.wycliffeassociates.demo.Network.*;

import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Date;
import java.util.HashSet;
import java.util.Scanner;

public class PositionServer implements ServerDiscoveryHandler {
	Server server;
	HashSet<Character> loggedIn = new HashSet();

	public PositionServer () throws IOException {
		server = new Server() {
			protected Connection newConnection () {
				return new CharacterConnection();
			}
		};
		server.setDiscoveryHandler(this);

		// For consistency, the classes to be sent over the network are
		// registered by the same method for both the client and server.
		Network.register(server);

		server.addListener(new Listener() {
			public void connected(Connection connection) {}

			public void idle(Connection connection) {}

			public void received (Connection c, Object object) {
				// We know all connections for this server are actually CharacterConnections.
				CharacterConnection connection = (CharacterConnection)c;
				Character character = connection.character;

				if (object instanceof Login) {
					// Ignore if already logged in.
					if (character != null) return;

					// Reject if the name is invalid.
					String name = ((Login)object).name;
					if (!isValid(name)) {
						c.close();
						return;
					}

					// Reject if already logged in.
					for (Character other : loggedIn) {
						if (other.name.equals(name)) {
							c.close();
							return;
						}
					}

					character = loadCharacter(name);

					// Reject if couldn't load character.
					if (character == null) {
						c.sendTCP(new RegistrationRequired());
						return;
					}

					loggedIn(connection, character);
					return;
				}

				if (object instanceof Register) {
					// Ignore if already logged in.
					if (character != null) return;

					Register register = (Register)object;

					// Reject if the login is invalid.
					if (!isValid(register.name)) {
						c.close();
						return;
					}

					// Reject if character alread exists.
					if (loadCharacter(register.name) != null) {
						c.close();
						return;
					}

					character = new Character();
					character.name = register.name;
					character.x = 0;
					character.y = 0;
					character.color = Color.HSBtoRGB((float)Math.random(),1f,1f);
					if (!saveCharacter(character)) {
						c.close();
						return;
					}

					loggedIn(connection, character);
					return;
				}

				if (object instanceof MoveCharacter) {
					// Ignore if not logged in.
					if (character == null) return;

					MoveCharacter msg = (MoveCharacter)object;

					// Ignore if invalid move.
					if (Math.abs(msg.x) != 1 && Math.abs(msg.y) != 1) return;

					character.x += msg.x;
					character.y += msg.y;

					UpdateCharacter update = new UpdateCharacter();
					update.id = character.id;
					update.x = character.x;
					update.y = character.y;
					server.sendToAllUDP(update);
					return;
				}

				if (object instanceof MoveFinishedCharacter) {
					// Ignore if not logged in.
					if (character == null) return;

					MoveFinishedCharacter msg = (MoveFinishedCharacter)object;

					// Ignore if invalid move.
					if (Math.abs(msg.x) != 1 && Math.abs(msg.y) != 1) return;

					character.x += msg.x;
					character.y += msg.y;
					saveCharacter(character);

					UpdateCharacter update = new UpdateCharacter();
					update.id = character.id;
					update.x = character.x;
					update.y = character.y;
					server.sendToAllTCP(update);
					return;
				}
			}

			private boolean isValid (String value) {
				if (value == null) return false;
				value = value.trim();
				if (value.length() == 0) return false;
				return true;
			}

			public void disconnected (Connection c) {
				CharacterConnection connection = (CharacterConnection)c;
				if (connection.character != null) {
					loggedIn.remove(connection.character);

					RemoveCharacter removeCharacter = new RemoveCharacter();
					removeCharacter.id = connection.character.id;
					server.sendToAllTCP(removeCharacter);

					System.out.println(c.getID() + " disconnected");
				}
			}
		});

		Scanner scanner = new Scanner(System.in);
		String name;
		System.out.println("Enter server name:");
		name = scanner.nextLine();

		if(!name.trim().isEmpty()) {
			server.setServerName(name);
			server.bind(Network.tcpPort, Network.udpPort);
			server.start();
		}
	}

	void loggedIn (CharacterConnection c, Character character) {
		character.started = new Date().getTime();
		c.character = character;

		// Add existing characters to new logged in connection.
		for (Character other : loggedIn) {
			AddCharacter addCharacter = new AddCharacter();
			addCharacter.character = other;
			c.sendTCP(addCharacter);
		}

		loggedIn.add(character);

		// Add logged in character to all connections.
		AddCharacter addCharacter = new AddCharacter();
		addCharacter.character = character;
		server.sendToAllTCP(addCharacter);
	}

	boolean saveCharacter (Character character) {
		File file = new File("characters", character.name.toLowerCase());
		file.getParentFile().mkdirs();

		if (character.id == 0) {
			String[] children = file.getParentFile().list();
			if (children == null) return false;
			character.id = children.length + 1;
		}

		DataOutputStream output = null;
		try {
			output = new DataOutputStream(new FileOutputStream(file));
			output.writeInt(character.id);
			output.writeInt(character.x);
			output.writeInt(character.y);
			output.writeInt(character.color);
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		} finally {
			try {
				output.close();
			} catch (IOException ignored) {
			}
		}
	}

	Character loadCharacter (String name) {
		File file = new File("characters", name.toLowerCase());
		if (!file.exists()) return null;
		DataInputStream input = null;
		try {
			input = new DataInputStream(new FileInputStream(file));
			Character character = new Character();
			character.id = input.readInt();
			character.name = name;
			character.x = input.readInt();
			character.y = input.readInt();
			character.color = input.readInt();
			input.close();
			return character;
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		} finally {
			try {
				if (input != null) input.close();
			} catch (IOException ignored) {
			}
		}
	}

	// This holds per connection state.
	static class CharacterConnection extends Connection {
		public Character character;
	}

	@Override
	public boolean onDiscoverHost(DatagramChannel datagramChannel, InetSocketAddress fromAddress, String serverName) throws IOException {
		datagramChannel.send(ByteBuffer.wrap(serverName.getBytes()), fromAddress);
		return true;
	}

	public static void main (String[] args) throws IOException {
		Log.set(Log.LEVEL_DEBUG);
		new PositionServer();
	}
}
