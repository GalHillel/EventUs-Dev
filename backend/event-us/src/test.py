import datetime
import time
import requests
import json

# api-endpoint
URL = "http://localhost:3000/"


def addProfilePic():
    print("-----------------Add profile pic test-----------------")
    with open("backend/event-us/src/testimg.png",'rb') as f:
        testImg = f.read()
    
    files1 = {
        "icon": ("testimg.png", testImg, "multipart/form-data")
    }
    r_files1 = requests.post(url = URL+"profilepics",files=files1)
    
    print(r_files1)
    
    return r_files1.json()
    
def addUser(name,email,password,user_type)->requests.Response:
    print("-----------------Add user-----------------")
    data_user1 = {
        "name": name,
        "email": email,
        "password": password,
        "user_type": user_type
    }
    r_user1 = requests.post(url = URL+"users/register",json=data_user1)
    time.sleep(1)
    return r_user1

#Organizer
def genUsers(names,u_type="Participant")->list[requests.Response]:
    
    emails = [i+"@gmail.com" for i in names]
    passwords = [i+"Pass" for i in names]
    old_users = []
    new_users = []
    for i in range(len(names)):
        r = requests.get(URL+"users?name="+names[i]).json()
        time.sleep(1)
        if len(r) == 0:
            u = addUser(names[i],emails[i],passwords[i],u_type)
            new_users.append(u.json())
        else:
            old_users.append(r[0])
    return new_users,old_users

def genEvents(names,o,dates):
    old_events = []
    new_events = []
    for i, name in enumerate(names):
        r = requests.get(URL+"events?name="+names[i]+"&creator_id="+o["_id"]).json()
        time.sleep(1)
        if len(r) == 0:
            data_event = {
                "name":name,
                "date":dates[i%len(dates)],
                "creator_id":o["_id"]
            }
            r = requests.post(url=URL+"events/create",data=data_event)
            new_events.append(r.json())
            time.sleep(1)
        else:
            old_events.append(r[0])
    return new_events,old_events

def joinEvents(users,events):
    for i,u in enumerate(users):
        requests.patch(url=URL+"events/"+events[i%len(events)]["_id"]+"/joinEvent",data={"_id":u["_id"]})
        time.sleep(1)

def genMessages(message_datas):
    for m in message_datas:
        requests.post(url=URL+"messages",data=m)
        time.sleep(1)


GEN_DATA = True
datetime_str1 = ['01/19/24 13:55:26','04/2/24 13:55:26','01/2/24 13:55:26','09/24/24 13:55:26','03/11/24 13:55:26']
datetime_str2 = ['03/19/24 13:55:26','10/2/24 13:55:26','04/3/24 13:55:26','11/22/24 13:55:26','07/13/24 13:55:26']
datetime_objects1 = [datetime.datetime.strptime(s, '%m/%d/%y %H:%M:%S') for s in datetime_str1]
datetime_objects2 = [datetime.datetime.strptime(s, '%m/%d/%y %H:%M:%S') for s in datetime_str2]

if GEN_DATA:
    names1 = ["user1","user2","user3","ziv","gal"]
    names2 = ["zivO","galO"]

    new_users,old_users = genUsers(names1)
    all_users = new_users+old_users

    new_orgs,old_orgs = genUsers(names2,"Organizer")
    all_orgs = new_orgs+old_orgs

    new_events1,old_events1 = genEvents(["ziv's Birthday", "Wedding","open day"],all_orgs[0],datetime_objects1)

    new_events2,old_events2 = genEvents(["Birthday", "Wedding","Havana Club"],all_orgs[1],datetime_objects2)

    new_events = new_events1 + new_events2
    old_events = old_events1 + old_events2

    attends = [[all_users[i] for i in range(3)],
    [all_users[i] for i in range(2)],
    [all_users[i] for i in range(4)],
    [all_users[-i] for i in range(1,3)],
    [all_users[i] for i in range(2,4)],
    [all_users[-i] for i in range(2,5)]]

    for i in range(len(new_events)):
        print("adding users to "+new_events[i]["name"])
        if(len(attends) > len(old_events)):
            joinEvents(attends[i+len(old_events)],[new_events[i]])
            m1 = {
                "sender_id":new_events[i]["creator_id"],
                "receiver_ids":[u["_id"] for u in attends[i+len(old_events)]],
                "title":"message about "+new_events[i]["name"],
                "content":"this is a message from the event creator of event: "+new_events[i]["name"]
            }
            genMessages([m1])

    time.sleep(1)

else:
    pfp = addProfilePic()
    #print(pfp)

    #_id = "65c7f47c8b594a9d9a89eafe"
    #r = requests.patch(url=URL+"users/65c7878a476d032000c6fe77/edit",data={"profile_pic":_id})
    #print(r)

