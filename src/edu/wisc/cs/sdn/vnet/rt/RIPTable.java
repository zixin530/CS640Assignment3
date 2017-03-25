package edu.wisc.cs.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

class RIPTable() {
	void initialize( Router router ) {
		for( Iface iface : router.interfaces.values() ) {
			sendRIPRequest( router, iface );
		}
	}

	void sendRIPRequest( Router router, Iface iface ) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		UDP udp = new UDP();	
		RIPv2 rip = new RIPv2();
		ether.setPayload( ip );
		ip.setPayload( udp );
		udp.setPayload( rip );

		ether.setEtherType( Ethernet.TYPE_IPv4 );
		ether.setDestinationMACAddress( "FF:FF:FF:FF:FF:FF" );
		ether.setSourceMACAddress( iface.getMacAddress().toBytes() );
		//ether.serialize();

		ip.setTtl( (byte)64 ).setProtocol( IPv4.PROTOCOL_UDP );
		ip.setDestinationAddress( IPv4.toIPv4Address( "224.0.0.9" ) ); 
		ip.setSourceAddress( iface.getIpAddress() );	
		//ip.serialize();

		udp.setSourcePort( RIP_PORT );
		udp.setDestinationPort( RIP_PORT );
		//udp.serialize();

		rip.setCommand( COMMAND_REQUEST );
		//rip.serialize();
	}

	void sendRIPResponse
}
