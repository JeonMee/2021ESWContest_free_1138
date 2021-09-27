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
#include <Arduino.h>
#include <math.h>
#include <DW1000Ng.hpp>
#include <DW1000NgUtils.hpp>
#include <DW1000NgRanging.hpp>
#include <DW1000NgRTLS.hpp>
#include <ESP8266WiFi.h>
#include <ESP8266WiFiMulti.h>
#include <WiFiClient.h>
#include <ESP8266HTTPClient.h>

ESP8266WiFiMulti WiFiMulti;

#ifndef STASSID
#define STASSID "COSMOS2G"
#define STAPSK  "code1234"
#endif

const char* ssid = "COSMOS2G";
const char* password = "code1234";

double Dis1;
double Dis2;
double Dis3;
String strD1;
String strD2;
String strD3;

typedef struct Position {
    double x;
    double y;
} Position;

// connection pins
#if defined(ESP8266)
const uint8_t PIN_SS = D8;
const uint8_t PIN_RST = D4;
#else
const uint8_t PIN_SCK = 13;
const uint8_t PIN_MOSI = 11;
const uint8_t PIN_MISO = 12;
const uint8_t PIN_SS = 10;
const uint8_t PIN_RST = 7;
const uint8_t PIN_IRQ = 8;
#endif

// Extended Unique Identifier register. 64-bit device identifier. Register file: 0x01
char EUI[] = "AA:BB:CC:DD:EE:FF:00:00";

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
   Serial.begin(115200);
   Serial.println();
   Serial.println();
   Serial.println();

   for (uint8_t t = 4; t > 0; t--) {
     Serial.printf("[SETUP] WAIT %d...\n", t);
     Serial.flush();
     delay(1000);
   }
 
    WiFi.mode(WIFI_STA);
    WiFiMulti.addAP("COSMOS2G", "code1234");

    while (WiFiMulti.run() != WL_CONNECTED) {
      delay(500);
      Serial.println("Waiting for connection");
    }
    Serial.println("connect!");
    
//    Serial.println(F("### DW1000Ng-arduino-ranging-anchorMain ###"));
    // initialize the driver
    #if defined(ESP8266)
    DW1000Ng::initializeNoInterrupt(PIN_SS, PIN_RST);
    #else
    DW1000Ng::initializeNoInterrupt(PIN_SS, PIN_RST);
    #endif
    Serial.println(F("DW1000Ng initialized ..."));
    // general configuration
    DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
    DW1000Ng::enableFrameFiltering(ANCHOR_FRAME_FILTER_CONFIG);
    
    DW1000Ng::setEUI(EUI);

    DW1000Ng::setPreambleDetectionTimeout(64);
    DW1000Ng::setSfdDetectionTimeout(273);
    DW1000Ng::setReceiveFrameWaitTimeoutPeriod(5000);

    DW1000Ng::setNetworkId(RTLS_APP_ID);
    DW1000Ng::setDeviceAddress(2);
	
    DW1000Ng::setAntennaDelay(16436);
    
    Serial.println(F("Committed configuration ..."));
    // DEBUG chip info and registers pretty printed
    char msg[128];
    DW1000Ng::getPrintableDeviceIdentifier(msg);
    Serial.print("Device ID: "); Serial.println(msg);
    DW1000Ng::getPrintableExtendedUniqueIdentifier(msg);
    Serial.print("Unique ID: "); Serial.println(msg);
    DW1000Ng::getPrintableNetworkIdAndShortAddress(msg);
    Serial.print("Network ID & Device Address: ");
    DW1000Ng::getPrintableDeviceMode(msg);
    Serial.print("Device mode: "); Serial.println(msg);   
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

void DataForTransmit(double Dis1, double Dis2, double Dis3){
  double X1 = 4.5;
  double Y1 = 4.8;

  //거리를 이용해 스마트폰 좌표 계산
  double xf1 = (pow(X1, 2) + pow(Dis1, 2) - pow(Dis2, 2))/(2*X1);

  double yf1 = (pow(Y1, 2) + pow(Dis1, 2) - pow(Dis3, 2))/(2*Y1);

  double zf1 = (sqrt(pow(Dis1, 2) - pow(xf1, 2) - pow(yf1, 2)));
  if(zf1 != zf1) zf1 = 0.0;
  if(zf1 >1.0) zf1 = 0.0;
  //서버로 데이터 전송
  /*
  doc["deviceID"] = "CMF0001";
  doc["pos_x"] = xf1;
  doc["pos_y"] = yf1;
  doc["pos_z"] = zf1;
  doc["pressure"] = 300.0;
  */
  String output = "{\"deviceID\":\"CMF0001\",\"pressure\":300,\"pos_x\":" + String(xf1) + ",\"pos_y\":" + String(yf1) + ",\"pos_z\":" + String(zf1) + "}";
  //String output = "{\"deviceID\":\"CMF0001\",\"pressure\":300,\"pos_x\":" + String(xf1) + ",\"pos_y\":" + String(yf1) + ",\"pos_z\":" + String(zf1) + "}";
  //serializeJson(doc, output);
//  Serial.println("lnnnnn");

  if(WiFiMulti.run() == WL_CONNECTED) {
    //WiFiClient client;
    HTTPClient http;
    http.begin("http://192.168.50.175:5000/FireExt");
    http.addHeader("Content-Type", "application/json");
    Serial.print("to server >> "); Serial.println(output.c_str());
    int httpCode = http.POST(output);
    //int httpCode = http.GET();
    
    String payload = http.getString();
    Serial.println("from server >> "+payload);
    http.end();
  }
  else {
    Serial.println("WiFi has disconnected");
  }
}

void loop() {
     if(DW1000NgRTLS::receiveFrame()){
//      Serial.println("HAWI");
        size_t recv_len = DW1000Ng::getReceivedDataLength();
        byte recv_data[recv_len];
        DW1000Ng::getReceivedData(recv_data, recv_len);


        if(recv_data[0] == BLINK) {
            DW1000NgRTLS::transmitRangingInitiation(&recv_data[2], tag_shortAddress);
            DW1000NgRTLS::waitForTransmission();

            if(DEFAULT_CONFIG.channel == Channel::CHANNEL_4) {
//              Serial.println("not ch4");
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              Dis1 = result.range;
              strD1 = String(Dis1, 4);
              //Serial.print((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
              DEFAULT_CONFIG.channel = Channel::CHANNEL_5;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            } 
            
            else if(DEFAULT_CONFIG.channel == Channel::CHANNEL_5) {
//              Serial.println("not ch5");
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              Dis2 = result.range;
              strD2 = String(Dis2, 4);
              //Serial.print((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
              DEFAULT_CONFIG.channel = Channel::CHANNEL_7;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            } 
            
            else if(DEFAULT_CONFIG.channel == Channel::CHANNEL_7) {
//              Serial.println("not ch7");
              RangeAcceptResult result = DW1000NgRTLS::anchorRangeAccept(NextActivity::RANGING_CONFIRM, next_anchor);
              if(!result.success) return;
              Dis3 = result.range;
              strD3 = String(Dis3, 4);
              Serial.println((String)"a"+(String)strD1+(String)"b"+(String)strD2+(String)"c"+(String)strD3+(String)"d");
              DataForTransmit(Dis1, Dis2, Dis3);
              DEFAULT_CONFIG.channel = Channel::CHANNEL_4;
              DW1000Ng::applyConfiguration(DEFAULT_CONFIG);
            }

            


        } else if(recv_data[9] == 0x60) {
            double range = static_cast<double>(DW1000NgUtils::bytesAsValue(&recv_data[10],2) / 1000.0);
            String rangeReportString = "Range "; rangeReportString += recv_data[7];
            rangeReportString += " = "; rangeReportString += range; rangeReportString += " m";
            Serial.println(rangeReportString);
            if(received_B == false && recv_data[7] == anchor_b[0] && recv_data[8] == anchor_b[1]) {
                range_B = range;
                Dis2 = range_B;
                received_B = true;                
            } else if(received_B == true && recv_data[7] == anchor_c[0] && recv_data[8] == anchor_c[1]){
                range_C = range;
                Dis3 = range_C;
                received_B = false;                
            } else {
                received_B = false;
            }
        }
    }
    
}
