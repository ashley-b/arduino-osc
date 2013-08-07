import java.lang.reflect.Method;
import java.nio.*;
import java.util.*;

import netP5.Logger;
import processing.core.*;
import processing.serial.*;
import oscP5.*;

class OscSerial {

	// SLIP Serial chars
	final int END = 0300;
	final int ESC = 0333;
	final int ESC_END = 0334;
	final int ESC_ESC = 0335;

	protected ArrayList serialBuffer;
	protected int count = 0;

	protected Serial serial;
	protected HashMap<String, ArrayList<OscPlug>> _myOscPlugMap = new HashMap<String, ArrayList<OscPlug>>();
    private OscProperties _myOscProperties;

	
	// --------------------------------------------------------------------------
	OscSerial(PApplet parent, Serial serial) {
		this.serial = serial;
		serialBuffer = new ArrayList();
		// register pre() event? for automatic listen
	}

	// --------------------------------------------------------------------------
	// Listen to data on the Serial port and assemble packets
	// There doesn't seem to be a way to hook into the serialEvent() callback
	// directly
	// so this method will need to be placed inside serialEvent()
	protected void listen() {
		try {
			while (serial.available() > 0) {
				int buffer = serial.read();
				switch (buffer) {
				case END:
					if (count > 0) {
						count = 0;
						process(); // <-- process the packet
						serial.clear();
						serialBuffer.clear();
						return;
					}
					break;
				case ESC:
					buffer = serial.read(); // <-- immediately read the next
											// char
					switch (buffer) {
					case ESC_END:
						buffer = END;
						break;
					case ESC_ESC:
						buffer = ESC;
						break;
					}
				default:
					serialBuffer.add(buffer);
					count++;
				}
			}
		}

		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// --------------------------------------------------------------------------
	public void send(OscMessage msg) {
		sendSLIP(msg.getBytes());
	}

	// --------------------------------------------------------------------------
	// TODO: needs to handle Bundles Vs Message
	// right now, assumes we are always getting Messages

	protected void process() {
		String address = "";
		int pos = 0;
		int b = (Integer) serialBuffer.get(pos++);
		ArrayList argumentTypes = new ArrayList();
		while (b != 0) {
			address += (char) b;
			b = (Integer) serialBuffer.get(pos++);
		}
		// Skip address zeros and the comma for the parameters
		pos += 4 - ((address.length()) % 4);
		b = (Integer) serialBuffer.get(pos++);
		while (b != 0) {
			argumentTypes.add((char) b);
			b = (Integer) serialBuffer.get(pos++);
		}

		// println(address);

		// println(pos);
		// Skip parameter zeros
		pos--;
		pos += 4 - ((argumentTypes.size() + 1) % 4);

		OscMessage oscMsg = new OscMessage(address);
		int data = -1;
		for (int i = 0; i < argumentTypes.size(); i++) {
			char type = (Character) argumentTypes.get(i);
			switch (type) {
			case 'i':
				int[] intArr = new int[4];
				intArr[0] = (Integer) serialBuffer.get(pos++);
				intArr[1] = (Integer) serialBuffer.get(pos++);
				intArr[2] = (Integer) serialBuffer.get(pos++);
				intArr[3] = (Integer) serialBuffer.get(pos++);
				data = byteArrayToInt(intArr, 0);
				oscMsg.add(data);
				break;
			case 'f':
				byte[] byteArr = new byte[4];
				byteArr[0] = (Byte) serialBuffer.get(pos++);
				byteArr[1] = (Byte) serialBuffer.get(pos++);
				byteArr[2] = (Byte) serialBuffer.get(pos++);
				byteArr[3] = (Byte) serialBuffer.get(pos++);
				float f = arr2float(byteArr, 0);
				oscMsg.add(f);
				break;
			case 's':
				String str = "";
				char c = (Character) serialBuffer.get(pos++);
				while (c != 0x00) {
					str += c;
					c = (Character) serialBuffer.get(pos++);
				}

				int zeros = 4 - (str.length() % 4);
				// Skip zeros
				for (int j = 0; j < zeros - 1; j++) {
					pos++;
				}
				oscMsg.add(str);
				break;
			}
		}

		callMethod(oscMsg);
		// notifyAll();

		// Trigger an oscEvent function.
		// in the Library version, this needs to be done like in ControlP5 by
		// registering with the parent applet
		// oscEvent(oscMsg);
	}

	// --------------------------------------------------------------------------
	protected int byteArrayToInt(int[] b, int offset) {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			value += (b[i + offset] & 0x000000FF) << shift;
		}
		return value;
	}

	// --------------------------------------------------------------------------
	protected float arr2float(byte[] buf, int pos) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.put(buf, pos, 4);
		return bb.getFloat(0);
	}

	// --------------------------------------------------------------------------
	protected void sendSLIP(byte[] packet) {
		int len = packet.length;
		serial.write(END);
		for (int i = 0; i < packet.length; i++) {
			switch (packet[i]) {
			case (byte) END:
				serial.write(ESC);
				serial.write(ESC_END);
				break;
			case (byte) ESC:
				serial.write(ESC);
				serial.write(ESC_END);
				break;
			default:
				serial.write(packet[i]);
			}
		}
		serial.write(END);
	}

	// The following functions are from the OscP5 class
	/**
	 * osc messages can be automatically forwarded to a specific method of an
	 * object. the plug method can be used to by-pass parsing raw osc messages -
	 * this job is done for you with the plug mechanism. you can also use the
	 * following array-types int[], float[], String[]. (but only as on single
	 * parameter e.g. somemethod(int[] theArray) {} ).
	 * 
	 * @param theObject
	 *            Object, can be any Object
	 * @param theMethodName
	 *            String, the method name an osc message should be forwarded to
	 * @param theAddrPattern
	 *            String, the address pattern of the osc message
	 * @param theTypeTag
	 *            String
	 * @example oscP5plug
	 * @usage Application
	 */
	public void plug(final Object theObject, final String theMethodName,
			final String theAddrPattern, final String theTypeTag) {
		final OscPlug myOscPlug = new OscPlug();
		myOscPlug.plug(theObject, theMethodName, theAddrPattern, theTypeTag);
		// _myOscPlugList.add(myOscPlug);
		if (_myOscPlugMap.containsKey(theAddrPattern)) {
			_myOscPlugMap.get(theAddrPattern).add(myOscPlug);
		} else {
			ArrayList<OscPlug> myOscPlugList = new ArrayList<OscPlug>();
			myOscPlugList.add(myOscPlug);
			_myOscPlugMap.put(theAddrPattern, myOscPlugList);
		}
	}

	/**
	 * @param theObject
	 *            Object, can be any Object
	 * @param theMethodName
	 *            String, the method name an osc message should be forwarded to
	 * @param theAddrPattern
	 *            String, the address pattern of the osc message
	 * @example oscP5plug
	 * @usage Application
	 */
	public void plug(final Object theObject, final String theMethodName,
			final String theAddrPattern) {
		final Class<?> myClass = theObject.getClass();
		final Method[] myMethods = myClass.getDeclaredMethods();
		Class<?>[] myParams = null;
		for (int i = 0; i < myMethods.length; i++) {
			String myTypetag = "";
			try {
				myMethods[i].setAccessible(true);
			} catch (Exception e) {
			}
			if ((myMethods[i].getName()).equals(theMethodName)) {
				myParams = myMethods[i].getParameterTypes();
				OscPlug myOscPlug = new OscPlug();
				for (int j = 0; j < myParams.length; j++) {
					myTypetag += myOscPlug.checkType(myParams[j].getName());
				}

				myOscPlug.plug(theObject, theMethodName, theAddrPattern,
						myTypetag);
				// _myOscPlugList.add(myOscPlug);
				if (_myOscPlugMap.containsKey(theAddrPattern)) {
					_myOscPlugMap.get(theAddrPattern).add(myOscPlug);
				} else {
					ArrayList<OscPlug> myOscPlugList = new ArrayList<OscPlug>();
					myOscPlugList.add(myOscPlug);
					_myOscPlugMap.put(theAddrPattern, myOscPlugList);
				}

			}
		}
	}

	private void callMethod(final OscMessage theOscMessage) {

		// forward the message to all OscEventListeners
		for (int i = listeners().size() - 1; i >= 0; i--) {
			((OscEventListener) listeners().get(i)).oscEvent(theOscMessage);
		}

		/* check if the arguments can be forwarded as array */

		if (theOscMessage.isArray) {
			// for (int i = 0; i < _myOscPlugList.size(); i++) {
			// OscPlug myPlug = ((OscPlug) _myOscPlugList.get(i));
			// if (myPlug.isArray && myPlug.checkMethod(theOscMessage, true)) {
			// invoke(myPlug.getObject(), myPlug.getMethod(),
			// theOscMessage.argsAsArray());
			// }
			// }

			if (_myOscPlugMap.containsKey(theOscMessage.addrPattern())) {
				ArrayList<OscPlug> myOscPlugList = _myOscPlugMap
						.get(theOscMessage.addrPattern());
				for (int i = 0; i < myOscPlugList.size(); i++) {
					OscPlug myPlug = (OscPlug) myOscPlugList.get(i);
					if (myPlug.isArray
							&& myPlug.checkMethod(theOscMessage, true)) {
						// Should we set the following here? The old code did
						// not:
						// theOscMessage.isPlugged = true;
						invoke(myPlug.getObject(), myPlug.getMethod(),
								theOscMessage.argsAsArray());
					}
				}
			}

		}
		/* check if there is a plug method for the current message */
		// for (int i = 0; i < _myOscPlugList.size(); i++) {
		// OscPlug myPlug = ((OscPlug) _myOscPlugList.get(i));
		// if (!myPlug.isArray && myPlug.checkMethod(theOscMessage, false)) {
		// theOscMessage.isPlugged = true;
		// invoke(myPlug.getObject(), myPlug.getMethod(), theOscMessage
		// .arguments());
		// }
		// }

		if (_myOscPlugMap.containsKey(theOscMessage.addrPattern())) {
			ArrayList<OscPlug> myOscPlugList = _myOscPlugMap.get(theOscMessage
					.addrPattern());
			for (int i = 0; i < myOscPlugList.size(); i++) {
				OscPlug myPlug = (OscPlug) myOscPlugList.get(i);
				if (!myPlug.isArray && myPlug.checkMethod(theOscMessage, false)) {
					theOscMessage.isPlugged = true;
					invoke(myPlug.getObject(), myPlug.getMethod(),
							theOscMessage.arguments());
				}
			}
		}

		/* if no plug method was detected, then use the default oscEvent mehtod */
		Logger.printDebug("OscP5.callMethod ", "" + isEventMethod);
		if (isEventMethod) {
			try {
				if (isOscIn) {
					invoke(parent, _myEventMethod, new Object[] { new OscIn(
							theOscMessage) });
					Logger.printDebug("OscP5.callMethod ", "invoking OscIn "
							+ isEventMethod);
				} else {
					invoke(parent, _myEventMethod,
							new Object[] { theOscMessage });
					Logger.printDebug("OscP5.callMethod ",
							"invoking OscMessage " + isEventMethod);
				}
			} catch (ClassCastException e) {
				Logger.printError("OscHandler.callMethod",
						" ClassCastException." + e);
			}
		}
	}

	private void invoke(final Object theObject, final Method theMethod,
			final Object[] theArgs) {
		try {
			theMethod.invoke(theObject, theArgs);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			Logger.printError(
					"OscP5",
					"ERROR. an error occured while forwarding an OscMessage\n "
							+ "to a method in your program. please check your code for any \n"
							+ "possible errors that might occur in the method where incoming\n "
							+ "OscMessages are parsed e.g. check for casting errors, possible\n "
							+ "nullpointers, array overflows ... .\n"
							+ "method in charge : " + theMethod.getName()
							+ "  " + e);
		}
	}

}
