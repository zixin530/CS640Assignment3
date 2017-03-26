package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import net.floodlightcontroller.packet.*;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	private final int ARP_REQUEST = 0;
	private final int ARP_REPLY = 1;
	private final String MAC_BROADCAST = "FF:FF:FF:FF:FF:FF";
	private final String MAC_ZERO = "00:00:00:00:00:00";
	private Map<Integer, List<Ethernet>> arpQueues;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.arpQueues = new ConcurrentHashMap<Integer, List<Ethernet>>();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
		// Ignore all other packet types, for now
		case Ethernet.TYPE_ARP:
			this.handleARPPacket(etherPacket, inIface);
		}
		
		/********************************************************************/
	}
	
	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum)
        { return; }
        
        // Check TTL
        ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
        if (0 == ipPacket.getTtl())
        { 
	//error message
	return; }
        
        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();
        
        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values())
        {
        	if (ipPacket.getDestinationAddress() == iface.getIpAddress())
        	{ return; }
        }
		
        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
	}

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
    {
        // Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        System.out.println("Forward IP packet");
		
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch)
        { return; }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface)
        { return; }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop)
        { nextHop = dstAddr; }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry)
        { 
	ArpLost(nextHop, etherPacket, inIface, outIface);	
	return; }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
        
        this.sendPacket(etherPacket, outIface);
    }
    private void handleARPPacket(Ethernet etherPacket, Iface inIface){
	ARP arpPacket = (ARP)etherPacket.getPayload();
        int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();	

	for (Iface iface : this.interfaces.values()){
		if (targetIp != iface.getIpAddress()) 
			return;
		if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
			sendARP(ARP_REPLY, 0, etherPacket, inIface, inIface);
			break;
		}
		else if (arpPacket.getOpCode() == ARP.OP_REPLY) {
			MACAddress mac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
			int ip = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
			arpCache.insert(mac, ip);
			synchronized(arpQueues){
				List<Ethernet> queue = arpQueues.remove(ip);
				if (queue != null){
					for (Ethernet packet : queue){
						packet.setDestinationMACAddress(mac.toBytes());
						sendPacket(packet, inIface);
					}
				}
			}
		}
	}
    }

    private void sendARP(int ArpType, int protAddr, Ethernet etherPacket, Iface inIface, Iface outIface){
	Ethernet ether = new Ethernet();
	ARP arp = new ARP();                                           
    	ether.setEtherType(Ethernet.TYPE_ARP);
	ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

	arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
	arp.setProtocolType(ARP.PROTO_TYPE_IP);
	arp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
	arp.setProtocolAddressLength((byte)4);
	arp.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
	arp.setSenderProtocolAddress(inIface.getIpAddress());

	 if(ArpType == ARP_REPLY)
		{
			
			ARP arpPacket = (ARP)etherPacket.getPayload();
			ether.setDestinationMACAddress(etherPacket.getSourceMACAddress());
			arp.setOpCode(ARP.OP_REPLY);
			arp.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
			arp.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());

		}
	else if(ArpType == ARP_REQUEST)
		{
		
			ether.setDestinationMACAddress(MAC_BROADCAST);
			arp.setOpCode(ARP.OP_REQUEST);
			arp.setTargetHardwareAddress(Ethernet.toMACAddress(MAC_ZERO));
			arp.setTargetProtocolAddress(protAddr);
		}
	else return;
	ether.setPayload(arp);
        this.sendPacket(ether, outIface);	
    }
    private void ArpLost(int ip, Ethernet etherPacket,Iface inIface, final Iface outIface){
	IPv4 ipv4 = (IPv4) etherPacket.getPayload();
        final Integer dstAddr = new Integer(ipv4.getDestinationAddress());
	
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);
	int temp = bestMatch.getGatewayAddress();
	if(bestMatch == null) return;
	if(0 == temp) temp = dstAddr;
	final int next = temp;
        synchronized(arpQueues)
		{
			if (arpQueues.containsKey(temp))
			{
				List<Ethernet> queue = arpQueues.get(temp);
				queue.add(etherPacket);
			}
			else 
			{
				List<Ethernet> queue = new ArrayList<Ethernet>();
				queue.add(etherPacket);
				arpQueues.put(temp, queue);
				TimerTask timertask = new TimerTask()
				{
					int count = 0;
					public void run()
					{
						if (arpCache.lookup(next) == null) 
						{
							if (count >= 3) 
							{
								arpQueues.remove(next);
								//TODO: send a message
								//sendICMP(DEST_HOST_UNREACHABLE, etherPacket, inIface);
								this.cancel();
							} 
							else 
							{
								sendARP(ARP_REQUEST, ip, etherPacket, inIface, outIface);
								count++;
							}
						}
						else this.cancel();
					}
				};
				// try send every 1000 ms
				Timer timer = new Timer(true);
				timer.schedule(timertask, 0, 1000);
			}
		}






















}
}
