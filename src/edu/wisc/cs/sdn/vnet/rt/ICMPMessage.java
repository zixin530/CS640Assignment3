package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ICMP;


// Provides the selected ICMP Message Packet

public class ICMPMessage {
	public void sendTimeExceeded( Router router, Ethernet original, RouteTable routeTable, ArpCache arpCache, Iface inIface ) {
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

		icmp.setIcmpType( (byte)11 ).setIcmpCode( (byte)0 );

		byte[] serializedIP = ipPacket.serialize();
		byte[] serializedBody = ipPacket.getPayload().serialize();
		byte[] body = new byte[ 12 + serializedIP.length ];		
		System.arraycopy( serializedIP, 0, body, 4, serializedIP.length );	
		System.arraycopy( serializedBody, 0, body, 4 + serializedIP.length, 8 );


		router.sendPacket( ether, outIface );
	}		
}

