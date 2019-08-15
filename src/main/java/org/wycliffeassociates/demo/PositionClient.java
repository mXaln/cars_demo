
package org.wycliffeassociates.demo;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.ClientDiscoveryHandler;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Listener.ThreadedListener;
import com.esotericsoftware.minlog.Log;
import org.wycliffeassociates.demo.Network.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;
import java.util.List;

public class PositionClient implements ClientDiscoveryHandler {
	UI ui;
	Client client;
	String name;
	Character me;

	public PositionClient () {
		client = new Client();
		client.setDiscoveryHandler(this);
		client.start();

		// For consistency, the classes to be sent over the network are
		// registered by the same method for both the client and server.
		Network.register(client);

		// ThreadedListener runs the listener methods on a different thread.
		client.addListener(new ThreadedListener(new Listener() {
			public void connected (Connection connection) {
			}

			public void received (Connection connection, Object object) {
				if (object instanceof RegistrationRequired) {
					Register register = new Register();
					register.name = name;
					client.sendTCP(register);
				}

				if (object instanceof AddCharacter) {
					AddCharacter msg = (AddCharacter)object;
					ui.addCharacter(msg.character);

					if(name.equals(msg.character.name)) {
						me = msg.character;
					}
					return;
				}

				if (object instanceof UpdateCharacter) {
					ui.updateCharacter((UpdateCharacter)object);

					Date now = new Date();
					int diff = (int)(now.getTime() - me.started) / 1000;

					// Check if me intersects others in 10 seconds after respawn
					if(diff > 10) {
						for(Map.Entry<Integer, Character> car: ui.characters.entrySet()) {
							diff = (int)(now.getTime() - car.getValue().started) / 1000;
							if(diff > 10 && isIntersected(me, car.getValue())) {
								long restartedTime = new Date().getTime();
								me.started = restartedTime;
								ui.gameOver(me.id);
							}
						}
					}
					return;
				}

				if (object instanceof RemoveCharacter) {
					RemoveCharacter msg = (RemoveCharacter)object;
					ui.removeCharacter(msg.id);
					return;
				}
			}

			public void disconnected (Connection connection) {
				System.exit(0);
			}

			public void idle(Connection connection) {

			}
		}));

		ui = new UI(client);

		List<Map.Entry<String, InetAddress>> inetAddrs = client.discoverHosts(Network.udpPort, 5000);
		String host = ui.chooseHost(inetAddrs);
		if(host == null) {
			host = getAddress(inetAddrs, ui.inputHost()).getHostAddress();
		}

		try {
			client.connect(5000, host, Network.tcpPort, Network.udpPort);
			// Server communication after connection can go here, or in Listener#connected().
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		name = ui.inputName();
		Login login = new Login();
		login.name = name;
		client.sendTCP(login);
	}

	public static InetAddress getAddress(List<Map.Entry<String, InetAddress>> inetAddrs, String name) {
		for(Map.Entry<String, InetAddress> addr: inetAddrs) {
			if(addr.getKey() == name) {
				return addr.getValue();
			}
		}
		return null;
	}

	public static String[] getServerNames(List<Map.Entry<String, InetAddress>> inetAddrs) {
		String[] servers = new String[inetAddrs.size()];
		for (int i = 0; i < inetAddrs.size(); i++) {
			servers[i] = inetAddrs.get(i).getKey();
			System.out.println(
					inetAddrs.get(i).getKey() + " (" + inetAddrs.get(i).getValue().getHostAddress() + ")"
			);
		}
		return servers;
	}

	public static boolean isIntersected(Character car1, Character car2) {
		if(car1.id == car2.id) return false;

		if((car1.x + 60) >= car2.x && (car2.x + 60) >= car1.x
			&& (car1.y + 60) >= car2.y && (car2.y + 60) >= car1.y) {
			return true;
		}

		return false;
	}

	static class UI {
		HashMap<Integer, Character> characters = new HashMap();
        HashMap<Integer, JLabel> cars = new HashMap();
        JFrame frame;
        Client client;

        public UI(Client client) {
            this.client = client;

            frame = new MainFrame(client);
            frame.setTitle("Client App");
            frame.setSize(400,500);
            Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(dim.width/2-frame.getSize().width/2, dim.height/2-frame.getSize().height/2);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.setLayout(null);
            frame.setVisible(true);
        }

		public String inputHost () {
			String input = (String)JOptionPane.showInputDialog(null, "Host:", "Connect to server", JOptionPane.QUESTION_MESSAGE,
				null, null, "localhost");
			if (input == null || input.trim().length() == 0) System.exit(1);
			return input.trim();
		}

		public String chooseHost (List<Map.Entry<String, InetAddress>> inetAddrs) {
			String[] servers = PositionClient.getServerNames(inetAddrs);
        	String input = (String)JOptionPane.showInputDialog(null, "Select host:", "Connect to server", JOptionPane.QUESTION_MESSAGE,
					null, servers, "localhost");
        	if(input != null) {
				return PositionClient.getAddress(inetAddrs, input).getHostAddress();
			}

        	return null;
		}

		public String inputName () {
			String input = (String)JOptionPane.showInputDialog(null, "Name:", "Connect to server", JOptionPane.QUESTION_MESSAGE,
				null, null, "Desk # ");
			if (input == null || input.trim().length() == 0) System.exit(1);
			return input.trim();
		}

		public void addCharacter (final Character character) {
			characters.put(character.id, character);

            JLabel label = new JLabel(character.name) {
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					g.drawString(character.name, 10, -20);
				}
			};
            Border border = BorderFactory.createLineBorder(Color.BLACK, 3);
            Color color = new Color(character.color);
            label.setBounds(character.x, character.y, 60, 60);
            label.setOpaque(true);
            label.setBackground(color);
            label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setFont(new Font("Serif", Font.PLAIN, 10));
            label.setBorder(border);
            frame.add(label);
            cars.put(character.id, label);

            refreshUI();

			System.out.println(character.name + " added at " + character.x + ", " + character.y);
		}

		public void updateCharacter (UpdateCharacter msg) {
        	Character character = characters.get(msg.id);
			if (character == null) return;
			character.x = msg.x;
			character.y = msg.y;

			JLabel car = cars.get(msg.id);
			if(car == null) return;
            car.setBounds(msg.x, msg.y, 60, 60);

			refreshUI();

			System.out.println(character.name + " moved to " + character.x + ", " + character.y);
		}

		public void removeCharacter (int id) {
			Character character = characters.remove(id);

            if (character != null) {
                JLabel car = cars.remove(id);
                if(car != null) {
                    frame.remove(car);
                    refreshUI();
                }

                System.out.println(character.name + " removed");
            }
		}

		public void refreshUI() {
            frame.invalidate();
            frame.validate();
            frame.repaint();
        }

        public void gameOver(int id) {
        	cars.get(id).setBackground(Color.BLACK);
			JOptionPane.showMessageDialog(frame, "Ooops!!!");
			System.exit(0);
		}
	}

	public static class MainFrame extends JFrame implements KeyListener {
        Client client;
		Set<Integer> pressed = new HashSet<>();

	    @Override
        public void keyTyped(KeyEvent e) {

        }

        @Override
        public synchronized void keyPressed(KeyEvent e) {
	    	if(pressed.size() < 2) {
				pressed.add(e.getKeyCode());
			}

			MoveCharacter msg = new MoveCharacter();

	    	if(pressed.size() == 1) {
				if(e.getKeyCode()== KeyEvent.VK_RIGHT)
					msg.x = 1;
				else if(e.getKeyCode()== KeyEvent.VK_LEFT)
					msg.x = -1;
				else if(e.getKeyCode()== KeyEvent.VK_DOWN)
					msg.y = 1;
				else if(e.getKeyCode()== KeyEvent.VK_UP)
					msg.y = -1;
			} else {
				if(pressed.contains(KeyEvent.VK_RIGHT) && pressed.contains(KeyEvent.VK_UP)) {
					msg.x = 1;
					msg.y = -1;
				} else if(pressed.contains(KeyEvent.VK_RIGHT) && pressed.contains(KeyEvent.VK_DOWN)) {
					msg.x = 1;
					msg.y = 1;
				} else if(pressed.contains(KeyEvent.VK_LEFT) && pressed.contains(KeyEvent.VK_DOWN)) {
					msg.x = -1;
					msg.y = 1;
				} else if(pressed.contains(KeyEvent.VK_LEFT) && pressed.contains(KeyEvent.VK_UP)) {
					msg.x = -1;
					msg.y = -1;
				}
			}

			if (msg != null) client.sendUDP(msg);
        }

        @Override
        public void keyReleased(KeyEvent e) {
			if(pressed.contains(e.getKeyCode())) {
				pressed.remove(e.getKeyCode());
			}
        }

        public MainFrame(Client client){
            addKeyListener(this);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            this.client = client;
        }
    }

	@Override
	public DatagramPacket onRequestNewDatagramPacket() {
		byte[] recvBuf = new byte[256];
		DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
		return packet;
	}

	@Override
	public void onDiscoveredHost(DatagramPacket datagramPacket) {
		// TODO
	}

	@Override
	public void onFinally() {

	}

	public static void main (String[] args) {
		Log.set(Log.LEVEL_DEBUG);
		new PositionClient();
	}
}
