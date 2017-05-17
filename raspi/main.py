import time
from datetime import datetime
import RPi.GPIO as io
import os
import glob
import socket
import sys
from thread import *
import json
import urllib2
from time import gmtime, strftime
from subprocess import call

HOST = ''
PORT = 8089
EMAIL = "saurav.pradhan@gmail.com"
PROJECT_HOME = "/home/pi/workspace/raspi-server-room-monitor/code/raspi/"

temp_c = 0
temp_f = 0
fan_state = False
auto_flag = True

req = urllib2.Request('https://gcm-http.googleapis.com/gcm/send')
req.add_header('Content-Type', 'application/json')
req.add_header('Authorization', 'key=AIzaSyCnhZfFUWQTyX_yHPY3tks3HTbVOELVb8E')

def sendPushNotification(msg):
  try:
    data = { "data": { "message": msg,"title":"Server Status"},"to" : "/topics/global"}
    response = urllib2.urlopen(req, json.dumps(data))
    print response
  except:
    print "no internet"

def handleSockets(s):
  while 1:
    conn, addr = s.accept()
    print 'Connected with ' + addr[0] + ':' + str(addr[1])
    start_new_thread(clientthread ,(conn,))

def clientthread(conn):
  global auto_flag
  global fan_state
  while True:
      data = conn.recv(1024)
      #print data
      if not data: 
          break
      elif 'F' in data:
        io.output(fan_pin,io.HIGH)
        fan_state = True
      elif 'f' in data:
        io.output(fan_pin,io.LOW)
        fan_state = False
      elif 'a' in data:
        auto_flag = True
      elif 'm' in data:
        auto_flag = False
      conn.sendall(str(int(temp_c))+","+str(fan_state)+","+str(io.input(pir_pin))+","+str(auto_flag)+"\n")
      #conn.sendall(reply)

  conn.close()

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
print 'Socket Created'

try:
    s.bind((HOST,PORT))
except socket.error as msg:
  print 'Bing failed. Error Code : '+str(msg[0])+' Message '+ msg[1]
  sys.exit()
print 'Socket Bind Complete'

s.listen(10)
print 'Socket now Listening'


io.setmode(io.BCM)
pir_pin = 18
fan_pin = 23
buzzer_pin = 24
io.setup(pir_pin, io.IN)
io.setup(fan_pin, io.OUT)
io.setup(buzzer_pin, io.OUT)

os.system('modprobe w1-gpio')
os.system('modprobe w1-therm')
 
base_dir = '/sys/bus/w1/devices/'
device_folder = glob.glob(base_dir + '28*')[0]
device_file = device_folder + '/w1_slave'
 
def read_temp_raw():
  f = open(device_file, 'r')
  lines = f.readlines()
  f.close()
  return lines
 
def read_temp():
  lines = read_temp_raw()
  while lines[0].strip()[-3:] != 'YES':
    time.sleep(0.2)
    lines = read_temp_raw()
  equals_pos = lines[1].find('t=')
  if equals_pos != -1:
    temp_string = lines[1][equals_pos+2:]
    temp_c = float(temp_string) / 1000.0
    temp_f = temp_c * 9.0 / 5.0 + 32.0
    return temp_c, temp_f
    
def sendEmail(msg):
    print "Sending Email to "+EMAIL
    call(["python",PROJECT_HOME+"sendEmail.py",EMAIL,msg])
    print "Email Sent to" + EMAIL


start_new_thread(handleSockets,(s,))

while True:
  if io.input(pir_pin):
    print "Intruder!!"
    io.output(buzzer_pin,io.HIGH)
    start_new_thread(sendPushNotification,("Someone just entered the serevr room.",))
    sendEmail("Someone just entered the Server Room at "+strftime("%l:%M %p on %d-%m-%Y"))
    time.sleep(3)
    io.output(buzzer_pin,io.LOW)
  temp_c,temp_f = read_temp()
  if auto_flag:
    if(temp_c>25):
      io.output(fan_pin,io.HIGH)
      if fan_state == False:
        start_new_thread(sendPushNotification,("Temperature above Threshold.",))
        sendEmail("Temperature above Threshold.")
      fan_state = True
    else:
      io.output(fan_pin,io.LOW)
      fan_state = False
  print temp_c
  time.sleep(0.5)
