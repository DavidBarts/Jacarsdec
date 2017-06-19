This is a sound-card ACARS decoder in Java. It should run anywhere that
Java runs and can access a sound-input device.

The biggest trick is determining the correct input device. If you run
the command:
    java -cp jacarsdec.jar name.blackcap.jacarsdec.Main --list
    
You'll see a list something like:
    
Mixer: 0
Name: Default Audio Device
Vendor: Unknown Vendor
Version: Unknown Version
Description: Direct Audio Device: Default Audio Device
Input 0: interface TargetDataLine supporting 14 audio formats, and buffers of at least 32 bytes
Output 0: interface SourceDataLine supporting 14 audio formats, and buffers of at least 32 bytes
Output 1: interface Clip supporting 14 audio formats, and buffers of at least 32 bytes

Mixer: 1
Name: Built-in Input
Vendor: Apple Inc.
Version: Unknown Version
Description: Direct Audio Device: Built-in Input
Input 0: interface TargetDataLine supporting 14 audio formats, and buffers of at least 32 bytes

Mixer: 2
Name: Built-in Output
Vendor: Apple Inc.
Version: Unknown Version
Description: Direct Audio Device: Built-in Output
Output 0: interface SourceDataLine supporting 14 audio formats, and buffers of at least 32 bytes
Output 1: interface Clip supporting 14 audio formats, and buffers of at least 32 bytes

Mixer: 3
Name: Port Built-in Input
Vendor: Apple Inc.
Version: Unknown Version
Description: Built-in Input
Output 0: LINE_IN source port

Mixer: 4
Name: Port Built-in Output
Vendor: Apple Inc.
Version: Unknown Version
Description: Built-in Output
Input 0: HEADPHONE target port

You will then have to experiment a bit to find what the mixer and device
numbers corresponding to the input device you want to use are. In my
case, it's mixer 1 ("Built-in Input"), input 0 ("interface
TargetDataLine ...").

So, assuming I'd be using 1-channel input, the command for my computer
would be:
    java -cp jacarsdec.jar name.blackcap.jacarsdec.Main 1 0
    
You'll probably want to put this command in a batch file or a shell
script so you won't have to specify the input information "by hand" each
time.

If your input device has multiple channels (the most common case being a
stereo input, which has separate left and right channels), then you may
need to specify the number of channels and which channel to use.
Jacarsdec tries to guess at the number of channels (it tries to open the
device in two-channel mode; if that fails, it tries again in one-channel
mode). You can use the --channels option to force Jacarsdec to open the
input device with the specified number of channels.

Channels are specified by a 0-based number. The industry standard for
assigning channel numbers to a stereo input is to assign the left
channel the lower number. So 0=left, 1==right. If you don't specify any
channel numbers for a stereo input, Jacarsdec will listen on channel 0
(left) only. Note that if you connect a monaural audio patch cable to a
stereo input, the audio will end up on the left channel, so the defaults
mean things will work as expected in the typical case of feeding
line-level audio output from a scanner into a stereo sound card input.

It is possible to listen and demodulate multiple input channels. To do
that, specify e.g. --select=0,1 on the command line. This can be
useful if you have two receivers and want to monitor two ACARS channels
simultaneously. You'll need the correct sort of Y-connector to merge
both monaural inputs into a single stereo input, of course.

This assumes your receiver or scanner has a line-level audio output
(often called a "tape" port). If it doesn't, you'll have to add one. How
to do so is beyond the scope of this document. I've had good luck with
using series resistors and blocking capacitors to make such things (it
takes some experimenting to find the proper values).

If you see messages like:
Jacarsdec: raw data lost on channel 0
or
Jacarsdec: demod data lost on channel 0

That means internal buffers in Jacarsdec are overflowing. You can use
the --input-size and --output-size to increase these. The default input
(raw data) buffer size is 50 messages (note that this is a per-channel
value), and the default output (demod data) buffer size is the input
size multiplied by the number of channels being demodulated. If no
amount of increasing buffer sizes seems to cure this problem, it means
your computer is too slow.