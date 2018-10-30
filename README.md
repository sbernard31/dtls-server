# dtls-server
A DTLS Server Command line tool based on Scandium which aims to make easier to test interoperability with other DTLS stack.

## How to use it 
Just download [dtls-server.jar](https://github.com/sbernard31/dtls-server/releases/download/0.1/dtls-server.jar).
Then,

```
> java -jar dtls-server.jar.
```
Usage :

```
> java -jar dtls-server.jar -h
Usage: <main class> [-hnx] [-i=<pskId>] [-k=<pskKey>] [-p=<port>]
  -h, --help               Show usage.
  -p, --port=<port>        The server port. (default 4433)
  -i, --pskid=<pskId>      The psk identity. (default 'Client_identity')
  -k, --pskkey=<pskKey>    The psk secret key in hexa. (default '73656372657450534b'
                             means 'secretKey')
  -n, --noRetransmission   Disable DTLS retransmission. It could be usefull for
                             debugging. (Currently disabling retransmission is not
                             possible so we delay it to 1 hour ...)
  -x, --exchangeRole       Use to test DTLS exchange role. Server wait until it
                             receive an application data, after it acts as a client
                             and try to initiate an handshake with the foreign peer,
                             then send to it a ACK again.
```

It is strongly advised to look what happens with tools like [Wireshark](https://www.wireshark.org/).

## Limitations
For now only PSK is supported by this tool.

## DTLS Role exchange
DTLS aims to secure exchange between 2 peers. In DTLS, one peer acts as a client and an other as a server. The DTLS client is just the one which initiates the DTLS handshake. This does not define how peer should act at Application Layer.

In common use case, those roles are fixed. Meaning a client act always as a client and a server act always as a server.

But with IoT or P2P application, this could make sense to have role exchange meaning that the client which initiates the handshake should be able to handle a new handshake initiates by a server. Why this could happened ? for example, because this server lost the DTLS connection after a reboot.

The concrete use case we are currently exploring is [LWM2M protocol](https://github.com/OpenMobileAlliance/OMA_LwM2M_for_Developers/issues/410).  
Here a wireshark capture illustrating this use case. This is done with scandium at device(port 36038) and server(port 5684) side. (using PSK) :

```
No.  Time          Source       Destination  SrcPort DesPort Protocol Length Info
   1 0.000000000   127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 133    Client Hello
   2 0.000359644   127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 102    Hello Verify Request
   3 0.005001722   127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 165    Client Hello
   4 0.005626495   127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 162    Server Hello, Server Hello Done
   5 0.042162424   127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 147    Client Key Exchange, Change Cipher Spec, Encrypted Handshake Message 
   6 0.061195906   127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 109    Change Cipher Spec, Encrypted Handshake Message
   7 0.062815631   127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 179    Application Data (LWM2M REGISTER request from device)
   8 0.081334961   127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 97     Application Data (LWM2M REGISTER response from server)
   9 8.483287786   127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 90     Application Data (LWM2M READ request from server)
  10 8.496936449   127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 213    Application Data (LWM2M READ response from client)
###  LWM2M Server (5684) Reboot and so lost its DTLS connection to LWM2M device (36038), ...
###  ... LWM2M Server will establish a new connection and so act as a DTLS client. 
  11 24.079310967  127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 151    Client Hello
  12 24.080362291  127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 102    Hello Verify Request
  13 24.083452354  127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 183    Client Hello
  14 24.085327257  127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 162    Server Hello, Server Hello Done
  15 24.110637371  127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 147    Client Key Exchange, Change Cipher Spec, Encrypted Handshake Message 
  16 24.111419901  127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 109    Change Cipher Spec, Encrypted Handshake Message 
  17 24.113519322  127.0.0.1    127.0.0.1    5684    36038   DTLSv1.2 92     Application Data (LWM2M READ request from server)
  18 24.114368265  127.0.0.1    127.0.0.1    36038   5684    DTLSv1.2 108    Application Data (LWM2M READ response from client)
```

### Test DTLS Role Exchange
To easily test exchange role with this tool you can use the -x (--exchangeRole) option : 
```
java -jar dtls-server.jar -i myPskId -k FE231ADE -x
```
(you can also test it manually using `send` and `clear` commands)

### Knowing stack which supports DTLS Role exchange.

[Scandium](https://github.com/eclipse/californium/tree/master/scandium-core) (java) (Tested with PSK)
[TinyDTLS](https://projects.eclipse.org/projects/iot.tinydtls) (C) (Tested with PSK)

