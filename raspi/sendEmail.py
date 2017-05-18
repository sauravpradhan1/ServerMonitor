#!/usr/bin/env python
import smtplib
from email.mime.text import MIMEText
from time import gmtime, strftime
import sys

USERNAME = "server.room.monitor.2017@gmail.com"
PASSWORD = "serverroom2017"

MAILTO = sys.argv[1]
MSG = sys.argv[2]

msg = MIMEText(MSG)
msg['to'] = MAILTO
msg['from'] = "Server Room"
msg['subject'] = "Server Alert at "+strftime("%l:%M %p on %d-%m-%Y")
 
server = smtplib.SMTP('smtp.gmail.com:587')
server.ehlo_or_helo_if_needed()
server.starttls()
server.ehlo_or_helo_if_needed()
server.login(USERNAME,PASSWORD)
server.sendmail(USERNAME, MAILTO, msg.as_string())
server.quit()
