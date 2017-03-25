package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ICMP;


// Provides the selected ICMP Message Packet

public class ICMPMessage {
	public static void sendTimeExceeded( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		ether.setEtherType( Ethernet.TYPE_IPv4 );

		IPv4 ipPacket = (IPv4)original.getPayload();
      	int dstAddr = ipPacket.getDestinationAddress();
      	// Find matching route table entry 
		RouteEntry bestMatch = routeTable.lookup(dstAddr);
      	// If no entry matched, do nothing
		Iface outIface = inIface;
		int nextHop = dstAddr;
      	if (null != bestMatch) { 
			// Make sure we don't sent a packet back out the interface it came in
		outIface = bestMatch.getInterface();
		nextHop = bestMatch.getGatewayAddress();
      	if (0 == nextHop)
      		{ nextHop = dstAddr; }
      	} 
		ether.setSourceMACAddress( outIface.getMacAddress().toBytes() ); 

      // Set destination MAC address in Ethernet header
      ArpEntry arpEntry = arpCache.lookup(nextHop);
      if (null == arpEntry)
      { return; }
      ether.setDestinationMACAddress(arpEntry.getMac().toBytes());
        	
		ip.setTtl( (byte)64 ).setProtocol( IPv4.PROTOCOL_ICMP ).setSourceAddress( inIface.getIpAddress() ).setDestinationAddress( ipPacket.getSourceAddress() );	

		setICMP( icmp, ipPacket, 11, 0 );
		setData( data, ipPacket );

		router.sendPacket( ether, outIface );
	}		

	public static void sendDestinationNetUnreachable( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		IPv4 ipPacket = (IPv4)original.getPayload();
	  	int srcAddr = ipPacket.getSourceAddress();
        setEther( ether, inIface, original, routeTable, arpCache );
		setIP( ip, inIface, srcAddr );
		setICMP( icmp, ipPacket, 3, 0 );
		setData( data, ipPacket );

		router.sendPacket( ether, inIface );
	}


	public static void sendDestinationHostUnreachable( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		IPv4 ipPacket = (IPv4)original.getPayload();
	  	int srcAddr = ipPacket.getSourceAddress();
        setEther( ether, inIface, original, routeTable, arpCache );
		setIP( ip, inIface, srcAddr );
		setICMP( icmp, ipPacket, 3, 1 );
		setData( data, ipPacket );

		router.sendPacket( ether, inIface );
	}

	public static void sendDestinationPortUnreachable( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();

		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		IPv4 ipPacket = (IPv4)original.getPayload();
	  	int srcAddr = ipPacket.getSourceAddress();
        setEther( ether, inIface, original, routeTable, arpCache );
		setIP( ip, inIface, srcAddr );
		setICMP( icmp, ipPacket, 3 , 3);
		setData( data, ipPacket );

		router.sendPacket( ether, inIface );
	}

	public static void sendEchoReply( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		//Data data = new Data();

		ether.setPayload(ip);
		ip.setPayload(icmp);
		//icmp.setPayload(data);

		IPv4 ipPacket = (IPv4)original.getPayload();
	  	int srcAddr = ipPacket.getSourceAddress();
		int destAddr = ipPacket.getDestinationAddress();

        setEther( ether, inIface, original, routeTable, arpCache );
		setIP( ip, destAddr, srcAddr );
		setICMP( icmp, ipPacket, 0 , 0);
		setEchoData( icmp, (ICMP)ipPacket.getPayload() );

		router.sendPacket( ether, inIface );
	}

	private static void setEther( Ethernet ether, Iface inIface, Ethernet original, RouteTable routeTable, ArpCache arpCache ) {
		ether.setEtherType( Ethernet.TYPE_IPv4 );
		ether.setSourceMACAddress( inIface.getMacAddress().toBytes() );

		IPv4 ipPacket = (IPv4)original.getPayload();
      
	  	int srcAddr = ipPacket.getSourceAddress();
      	  
		// Find matching route table entry 
		RouteEntry bestMatch = routeTable.lookup(srcAddr);
      	// If no entry matched, do nothing
		
		if( null == bestMatch ) {
			// drop
			return;
		}
		
		// Make sure we don't sent a packet back out the interface it came in
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop) { 
			nextHop = srcAddr; 
		}

      	// Set destination MAC address in Ethernet header
      	ArpEntry arpEntry = arpCache.lookup(nextHop);
      	if (null == arpEntry) { 
			// drop
			return; 
		}

      	ether.setDestinationMACAddress(arpEntry.getMac().toBytes());
	}

	private static void setIP( IPv4 ip, Iface inIface, int srcAddr ) {
		ip.setTtl( (byte)64 ).setProtocol( IPv4.PROTOCOL_ICMP ).setSourceAddress( inIface.getIpAddress() ).setDestinationAddress( srcAddr );
	}

	private static void setIP( IPv4 ip, int destAddr, int srcAddr ) {
		ip.setTtl( (byte)64 ).setProtocol( IPv4.PROTOCOL_ICMP ).setSourceAddress( destAddr ).setDestinationAddress( srcAddr );
	}

	private static void setICMP( ICMP icmp, IPv4 ipPacket, int type, int code ) {
		icmp.setIcmpType( (byte)type ).setIcmpCode( (byte)code );
	}

	private static void setData( Data data, IPv4 ipPacket ) {
		byte[] serializedIP = ipPacket.serialize();
		byte[] serializedBody = ipPacket.getPayload().serialize();
		byte[] body = new byte[ 12 + serializedIP.length ];		
		System.arraycopy( serializedIP, 0, body, 4, serializedIP.length );	
		System.arraycopy( serializedBody, 0, body, 4 + serializedIP.length, 8 );

		data.setData( body );
	}

	private static void setEchoData( ICMP icmp, ICMP icmpOriginal ) {
		icmp.setPayload( icmpOriginal.getPayload() );	
	}
}

