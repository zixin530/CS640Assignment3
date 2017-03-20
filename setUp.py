from subprocess import Popen, PIPE, STDOUT
import fileinput
import os

with open( os.devnull, 'w' ) as devnull:
	for line in fileinput.input():
		line = line.rstrip( '\n' )
		if( line.startswith( "s" ) ):
			print( "java -jar VirtualNetwork.jav -v " + line )
			Popen( "java -jar VirtualNetwork.jar -v " + line, shell=True )	
		elif( line.startswith( "r" ) ):
			print( "java -jar VirtualNetwork.jar -v " + line + " -r rtable." + line + " -a arp_cache" )
			Popen( "java -jar VirtualNetwork.jar -v " + line + " -r rtable." + line + " -a arp_cache", shell=True )	
