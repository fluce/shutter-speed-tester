const int analogInPin = A0;  // Analog input pin that the potentiometer is attached to
const int analogOutPin = 9; // Analog output pin that the LED is attached to

const long pulseInTimeout = 10000000; 

long sensorValue = 0;        // value read from the pot
int outputValue = 0;        // value output to the PWM (analog out)
long counter=0;

byte mode = 0; 

long lastMicros=0;
long lastMicros2=0;

#define BUFFER_SIZE 512
#define MAGIC "SHTR10 "

void setup() {
  // initialize serial communications at 9600 bps:
  Serial.begin(115200); 
  while (!Serial) {}
  Serial.println(MAGIC "MODE ");
  Serial.println(mode);
  pinMode(analogInPin,INPUT);
  
}

typedef struct record {
  unsigned int relTime4;
  byte value;
};

record rec[BUFFER_SIZE];
int currentPtr=0;
byte previousValue;

void serialEvent() {
  while (Serial.available())
  {
     String s=Serial.readString();
     if (s.startsWith("HELLO"))
     {
       Serial.print(MAGIC "MODE ");
       Serial.println(mode);
     } 
     else if (s.startsWith("0")) 
     {
       mode=0;
       Serial.println(MAGIC "MODE 0");
     } 
     else if (s.startsWith("1")) 
     {
       mode=1;
       Serial.println(MAGIC "MODE 1");
     } 
     else if (s.startsWith("2")) 
     {
       mode=2;
       Serial.println(MAGIC "MODE 2");
     }
     else if (s.startsWith("3")) 
     {
       mode=3;
       Serial.println(MAGIC "MODE 3");
     }
  }
  


}

void loop() {
  
  switch(mode) {
  
    case 3: 
    {
      long t=micros();
      sensorValue = analogRead(analogInPin); 
      counter++;
      Serial.print("M3 ");                
      Serial.print(t);                
      Serial.print(" " );                       
      Serial.println(sensorValue);
      if (!(counter&1023)) serialEvent();
      break;  
    }
    case 1: {
      long t=micros();
      sensorValue = analogRead(analogInPin); 
      byte v=sensorValue>>5;
      unsigned long elapsed2=(t-lastMicros2);
      lastMicros2=t;
      unsigned long elapsed=(t-lastMicros)>>2;
      if (v!=previousValue || elapsed2>100000 || elapsed>48000) {
        lastMicros=t;
        rec[currentPtr].relTime4=(elapsed)>0xFFFF?0xFFFF:elapsed;
        rec[currentPtr].value=v;
        previousValue=v;
        ++currentPtr &= (BUFFER_SIZE-1);
      }
      counter++;
      if (currentPtr==0) {
        for (int i=0;i<BUFFER_SIZE;i++) {
          Serial.print("M1 " );                       
          Serial.print(i);                       
          Serial.print(" " );                       
          Serial.print(rec[i].relTime4<<2);                
          Serial.print(" " );                       
          Serial.println(rec[i].value);  
        }
      }    
      if (!(counter&1023)) serialEvent();
      break;
    }
      
    case 2:
     { 
      sensorValue = pulseIn(analogInPin,LOW,pulseInTimeout);            
      Serial.print("M2 " );                       
      Serial.println(sensorValue);      
      serialEvent();
      break;
     }
    default:
      serialEvent();
  }
}

