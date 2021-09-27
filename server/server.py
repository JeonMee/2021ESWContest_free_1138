# 서버 -> 용인
# - 아두이노에서 디바이스 id/위치/압력 값 받아서 저장하기
# - 스마트폰이 요청하면 가장 가까운 디바이스 id/위치/압력 값 보내기
# python-flask

from flask import Flask
import json
from flask import request
import math
from flask.wrappers import Response

app = Flask(__name__)

fire_ext_list = dict()
device_id_1 = 'CMF0000'
device_id_2 = 'CMF0001'

@app.route('/')
def hello_world():
    return 'test string...'

class Pos():
    def __init__(self, pos):
        self.x = float(pos['x'])
        self.y = float(pos['y'])
        self.z = float(pos['z'])

class FireExt():
    def __init__(self, id, x, y, z, p=None):
        self.deviceID = id
        self.pos = Pos({'x':x,'y':y,'z':z,})
        if p is not None:
            self.pressure = float(p)

    def update(self, x, y, z, p=None):
        self.pos = Pos({'x':x,'y':y,'z':z,})
        if p is not None:
            self.pressure = float(p)
    
    def update_pressure(self, p):
        self.pressure = float(p)

def get_distance(pos_i, pos_f):
    return round(math.sqrt((pos_f.x - pos_i.x)**2 + (pos_f.y - pos_i.y)**2 + (pos_f.z - pos_i.z)**2),5)  # 소수점 5번째 자리까지만 계산

def get_nearest_extinguisher(pos): # x,y,z 값에 유저의 위치가 들어간다. 가장 가까운 거리의 소화기 id,위치,압력값을 반환한다.
    min_distance = 100000
    nearest_fire_ext = None
    for item in fire_ext_list.values():
        distance = get_distance(item.pos, pos)
        if not 780 < item.pressure < 960:
            continue
        if distance  < min_distance:
            min_distance = distance
            nearest_fire_ext = item
        
    return nearest_fire_ext

@app.route('/Position', methods=['GET', 'PUT']) # 가장 가까운 소화기  id, 위치, 압력값 반환
def handle_get_position():
    if request.method == 'PUT':
        #body = json.loads(json.loads(request.get_json())['nameValuePairs'])
        body = request.get_json()['nameValuePairs']
        request_pos = Pos(body)
        print('request_pos', request_pos)

        nearest_fire_ext = get_nearest_extinguisher(request_pos)
        if nearest_fire_ext is not None:
            response_obj = {
                'deviceID': nearest_fire_ext.deviceID,
                'pos_x': nearest_fire_ext.pos.x,
                'pos_y': nearest_fire_ext.pos.y,
                'pos_z': nearest_fire_ext.pos.z,
                'pressure': nearest_fire_ext.pressure
            }
        else:
            response_obj = {
                'deviceID': 'CMF0000',
                'pos_x': 0.0,
                'pos_y': 0.0,
                'pos_z': 0.0,
                'pressure': 0.0
            }
        print('<response extinguisher>', response_obj)
        return json.dumps(response_obj, indent = 4)

@app.route('/FireExt', methods=['GET', 'POST']) # 소화기 id,위치,압력값 저장
def handle_post_fire_ext():
    if request.method == 'POST':
       # body = json.loads(request.get_json())
        body = request.get_json()

        print('post_device_id', device_id_2)
        print('post_location', body['pos_x'], body['pos_y'], body['pos_z'])

        if device_id_2 in fire_ext_list.keys():
            fire_ext_list[device_id_2].update(body['pos_x'], body['pos_y'], body['pos_z'])
        else:
            fire_ext_list[device_id_2] = FireExt(body['deviceID'], body['pos_x'], body['pos_y'], body['pos_z'])
        
        print('fire_ext_list: ', fire_ext_list)  #리스트 목록 출력
        return body['deviceID']

@app.route('/Pressure', methods=['GET', 'PUT']) # 소화기 id,위치,압력값 저장
def handle_post_pressure():
    if request.method == 'PUT':
        #body = json.loads(json.loads(request.get_json())['nameValuePairs'])
        body = request.get_json()['nameValuePairs']
        print(body, type(body))

        # print('post_device_id', body['deviceID'])
        # print('post_location', body['pos_x'], body['pos_y'], body['pos_z'])
        # print('post_pressure', body['pressure'])

        # if body['deviceID'] in fire_ext_list.keys():
        #     fire_ext_list[body['deviceID']].update(body['pos_x'], body['pos_y'], body['pos_z'], body['pressure'])
        # else:
        #     fire_ext_list[body['deviceID']] = FireExt(body['deviceID'], body['pos_x'], body['pos_y'], body['pos_z'], body['pressure'])
        
        # print('fire_ext_list: ', fire_ext_list)  #리스트 목록 출력
        # return body['deviceID']
        # -------------------------------------------------------------------------------------------------------------
        pressure_1 = body['pressure1']
        pressure_2 = body['pressure2']
        print('fix_device_id', device_id_1) # 소화기1(고정되어있는거)
        print('fix_location', ('-2.7, 0, 0')) # 고정되어있는 소화기1의 위치 임의로 2.0으로 설정
        print('post_pressure1', pressure_1) # 소화기1의 압력값은 관리자앱에서 받아오는 pressure1로 설정
        print('ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ')
        print('post_device_id', device_id_2) # 소화기2(고정되어있는거)
        if device_id_2 in fire_ext_list.keys():
            print('post_location', fire_ext_list[device_id_2].pos.x, fire_ext_list[device_id_2].pos.y, fire_ext_list[device_id_2].pos.z) # 소화기2의 위치는 칩에서 전송하는 위치좌표로 설정
        else:
            print('post_location', 0.0, 0.0, 0.0) # 소화기2의 위치는 칩에서 전송하는 위치좌표로 설정
        print('post_pressure2',pressure_2) # 소화기1의 압력값은 관리자앱에서 받아오는 pressure2로 설정

        if device_id_1 in fire_ext_list.keys():
            fire_ext_list[device_id_1].update(8.55, 2.25, 0, pressure_1)
        else:
            fire_ext_list[device_id_1] = FireExt(device_id_1, 8.55, 2.25, 0, pressure_1)
        
        if device_id_2 in fire_ext_list.keys():
            fire_ext_list[device_id_2].update_pressure(pressure_2)
        else:
            fire_ext_list[device_id_2] = FireExt(device_id_2, 0.0, 0.0, 0.0, pressure_2)

        print('Fire_ext_list : ', fire_ext_list)
        
        return f"{device_id_1}, {device_id_2}"
if __name__ == '__main__':
    app.run(host = '192.168.50.175', debug=True)