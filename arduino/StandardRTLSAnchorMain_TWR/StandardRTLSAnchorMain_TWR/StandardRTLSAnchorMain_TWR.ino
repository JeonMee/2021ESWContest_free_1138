/*
   This example code is in the Public Domain (or CC0 licensed, at your option.)

   Unless required by applicable law or agreed to in writing, this
   software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
   CONDITIONS OF ANY KIND, either express or implied.
*/

/* 
 * StandardRTLSAnchorMain_TWR.ino
 * 
 * This is an example master anchor in a RTLS using two way ranging ISO/IEC 24730-62_2013 messages
 */

#include <DW1000Ng.hpp>
#include <DW1000NgUtils.hpp>
#include <DW1000NgRanging.hpp>
#include <DW1000NgRTLS.hpp>
//#include <Wire.h>

double D1;
double D2;
double D3;
String strD1;
String strD2;
String strD3;

typedef struct Position {
    double x;
    double y;
} Position;

// connection pins
const uint8_t PIN_SCK = 13;
const uint8_t PIN_MOSI = 11;
const uint8_t PIN_MISO = 12;
const uint8_t PIN_SS = 10;
const uint8_t PIN_RST = 7;
const uint8_t PIN_IRQ = 8;

// Extended Unique Identifier register. 64-bit device identifier. Register file: 0x01
char EUI[] = "AA:BB:CC:DD:EE:FF:00:01";

Position position_self = {0,0};
Position position_B = {3,0};
Position position_C = {3,2.5};

double range_self;
double range_B;
double range_C;

String rangeString;

boolean received_B = false;

byte target_eui[8];
byte tag_shortAddress[] = {0x05, 0x00};

byte anchor_b[] = {0x02, 0x00};
uint16_t next_anchor = 2;
byte anchor_c[] = {0x03, 0x00};

device_configuration_t DEFAULT_CONFIG = {
    false,
    true,
    true,
    true,
    false,
    SFDMode::STANDARD_SFD,
    Channel::CHANNEL_4,
    DataRate::RATE_850KBPS,
    PulseFrequency::FREQ_16MHZ,
    PreambleLength::LEN_256,
    PreambleCode::CODE_3
};

frame_filtering_configuration_t ANCHOR_FRAME_FILTER_CONFIG = {
    false,
    false,
    true,
    false,
    false,
    false,
    false,
    true /* This allows blink frames */
};

void setup() {
    // DEBUG monitoring
    Serial.begin(500000);
//    Serial.println(F("### DW1000Ng-arduino-ranging-anchorMain ###"));
    // initialize the driver
    #if defined(ESP8266)
    DW1000Ng::initializeNoInterrupt(PIN_SS);
    #else
    DW1000Ng::initializeNoInterrupt(PIN_SS, PIN_RST);
    #endif
//    Serial.println(F("DW1000Ng initialized ..."));
    // general configuration
    DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
    DW1000Ng::enableFrameFiltering(ANCHOR_FRAME_FILTER_CONFIG);
    
    DW1000Ng::setEUI(EUI);

    DW1000Ng::setPreambleDetectionTimeout(64);
    DW1000Ng::setSfdDetectionTimeout(273);
    DW1000Ng::setReceiveFrameWaitTimeoutPeriod(5000);

    DW1000Ng::setNetworkId(RTLS_APP_ID);
    DW1000Ng::setDeviceAddress(1);
	
    DW1000Ng::setAntennaDelay(16436);
    
//    Serial.println(F("Committed configuration ..."));
    // DEBUG chip info and registers pretty printed
    char msg[128];
    DW1000Ng::getPrintableDeviceIdentifier(msg);
//    Serial.print("Device ID: "); Serial.println(msg);
    DW1000Ng::getPrintableExtendedUniqueIdentifier(msg);
//    Serial.print("Unique ID: "); Serial.println(msg);
    DW1000Ng::getPrintableNetworkIdAndShortAddress(msg);
//    Serial.print("Network ID & Device Address: "); Serial.println(msg);
    DW1000Ng::getPrintableDeviceMode(msg);
//    Serial.print("Device mode: "); Serial.println(msg);   
    //Wire.begin(); 
}

/* using https://math.stackexchange.com/questions/884807/find-x-location-using-3-known-x-y-location-using-trilateration */
void calculatePosition(double &x, double &y) {

    /* This gives for granted that the z plane is the same for anchor and tags */
    double A = ( (-2*position_self.x) + (2*position_B.x) );
    double B = ( (-2*position_self.y) + (2*position_B.y) );
    double C = (range_self*range_self) - (range_B*range_B) - (position_self.x*position_self.x) + (position_B.x*position_B.x) - (position_self.y*position_self.y) + (position_B.y*position_B.y);
    double D = ( (-2*position_B.x) + (2*position_C.x) );
    double E = ( (-2*position_B.y) + (2*position_C.y) );
    double F = (range_B*range_B) - (range_C*range_C) - (position_B.x*position_B.x) + (position_C.x*position_C.x) - (position_B.y*position_B.y) + (position_C.y*position_C.y);

    x = (C*E-F*B) / (E*A-B*D);
    y = (C*D-A*F) / (B*D-A*E);
}

void loop() {
    if(DW1000NgRTLS::receiveFrame()){
        size_t recv_len = DW1000Ng::getReceivedDataLength();
        byte recv_data[recv_len];
        DW1000Ng::getReceivedData(recv_data, recv_len);


        if(recv_data[0] == BLINK) {
            DW1000NgRTLS::transmitRangingInitiation(&recv_data[2], tag_shortAddress);
            DW1000NgRTLS::waitForTransmission();

            if(DEFAULT_CONFIG.channel == Channel::CHANNEL_4) {
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              D1 = result.range;
              strD1 = String(D1, 4);
              //Serial.print((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
//              rangeString = "D1 = "; rangeString += String(D1, 4); rangeString += " m";
//              rangeString += "\t RX power: "; rangeString += DW1000Ng::getReceivePower(); rangeString += " dBm";
//              Serial.println(rangeString);
              DEFAULT_CONFIG.channel = Channel::CHANNEL_5;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            } 
            
            else if(DEFAULT_CONFIG.channel == Channel::CHANNEL_5) {
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              D2 = result.range;
              strD2 = String(D2, 4);
              //Serial.print((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
//              rangeString = "D2 = "; rangeString += String(D2, 4); rangeString += " m";
//              rangeString += "\t RX power: "; rangeString += DW1000Ng::getReceivePower(); rangeString += " dBm";
//              Serial.println(rangeString);
              DEFAULT_CONFIG.channel = Channel::CHANNEL_7;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            } 
            
            else if(DEFAULT_CONFIG.channel == Channel::CHANNEL_7) {
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              D3 = result.range;
              strD3 = String(D3, 4);
//              rangeString = "D3 = "; rangeString += String(D3, 4); rangeString += " m";
//              rangeString += "\t RX power: "; rangeString += DW1000Ng::getReceivePower(); rangeString += " dBm";
//              Serial.println(rangeString);
              Serial.print((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
              DEFAULT_CONFIG.channel = Channel::CHANNEL_4;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            }

            

//            RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
//            if(!result.success) return;
//
//            range_self = result.range;
            
//            rangeString = "Range = "; rangeString += String(range_self, 4); rangeString += " m";
//            rangeString += "\t RX power: "; rangeString += DW1000Ng::getReceivePower(); rangeString += " dBm";
//            Serial.println(rangeString);

            //Serial.print((String)"a"+D1+(String)"b"+D2+(String)"c"+D3+(String)"d");
            


        } else if(recv_data[9] == 0x60) {
            double range = static_cast<double>(DW1000NgUtils::bytesAsValue(&recv_data[10],2) / 1000.0);
            String rangeReportString = "Range "; rangeReportString += recv_data[7];
            rangeReportString += " = "; rangeReportString += range; rangeReportString += " m";
            Serial.println(rangeReportString);
            //Serial.print((String)"a"+D1+(String)"b"+D2+(String)"c"+D3+(String)"d");
            if(received_B == false && recv_data[7] == anchor_b[0] && recv_data[8] == anchor_b[1]) {
                range_B = range;
                D2 = range_B;
                received_B = true;                
            } else if(received_B == true && recv_data[7] == anchor_c[0] && recv_data[8] == anchor_c[1]){
                range_C = range;
                D3 = range_C;
//                double x,y;
//                calculatePosition(x,y);
//                String positioning = "Found position - x: ";
//                positioning += x; positioning +=" y: ";
//                positioning += y;
                //Serial.println(positioning);
                received_B = false;                
            } else {
                received_B = false;
            }
        }
    }
//      Serial.println(rangeString);
//      delay(50);
    

//    Serial.println((String)"D1 = " + D1);
//    Serial.println((String)"D2 = " + D2);
//    Serial.println((String)"D3 = " + D3);
//    Serial.println();
    //Serial.print((String)"a"+(D1)+(String)"b"+(D2)+(String)"c"+(D3)+(String)"d0.00e0.00f0.00g");
    //delay(1);

//    
//    Serial.println();
//            Wire.requestFrom(0, 8);
//             while(Wire.available()) {
//                for(int i=0; i<8; i++) {
//                  cD2[i] = Wire.read();
//                }
//              }
//            Wire.requestFrom(1, 8);
//            while(Wire.available()) {
//              for(int i=0; i<8; i++) {
//              cD3[i] = Wire.read();
//               }
//            }
//            
//            for(char b=0; b<8; b++) {
//              strD2.setCharAt(b, cD2[b]);
//            }
//            for(char b=0; b<8; b++) {
//              strD3.setCharAt(b, cD3[b]);
//            }
//
//            D2 = strD2.toDouble();
//            D3 = strD3.toDouble();
//      Serial.println((String)"a"+D1+(String)"b"+D2+(String)"c"+D3+(String)"d");
//      delay(10);
    
}
