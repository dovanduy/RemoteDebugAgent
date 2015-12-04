package com.aiitec.debugAgent.server;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.aiitec.debugAgent.DeviceDesc;
import com.aiitec.debugAgent.OnCloseCallback;
import com.aiitec.debugAgent.StreamUtils;


public class TcpBridge implements Runnable{

	static Logger log = LogManager.getLogger(TcpBridge.class);
	int port = 5556;
	private ServerSocket mobileServerSocket;
	private ServerSocket adbServerSocket;
	private ArrayList<MobileConnection> mobileConnectionList = new ArrayList<MobileConnection>();
	private ArrayList<AdbConnection> adbConnectionList = new ArrayList<AdbConnection>();
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TcpBridge tcp  = new TcpBridge();
		new Thread(tcp).start();
		
	}
	
	public void mobileServerRun() {
		try {
			mobileServerSocket = new ServerSocket(port);
			log.info("�������豸����ʼ����");
			while (true) {
				
				Socket s = mobileServerSocket.accept();
				handlerMobileSocket(s);
			}

		} catch (IOException e) {
			log.error("ͨѶ�쳣",e);
			if (mobileServerSocket.isClosed()) {
				log.info("�豸�������˿ڹر�");
			} else {
				log.info("�豸��������ʧ��");
				
			}
		}
	}
	private void adbServerRun() {
		try {
			adbServerSocket = new ServerSocket(4555);
			log.info("Adb�����������ʼ����");
			while (true) {
				
				Socket s = adbServerSocket.accept();
				handlerAdbSocket(s);
			}

		} catch (Throwable e) {
			if (adbServerSocket.isClosed()) {
				log.info("Adb�������˿ڹر�");
			} else {
				log.info("Adb��������ʧ��");
				e.printStackTrace();
			}
		}
	}


	private void handlerAdbSocket(Socket adbSocket) {
		log.info("ADB AGENT �ͻ���������"+adbSocket.getRemoteSocketAddress());
		InputStream is;
		try {
			is = adbSocket.getInputStream();
			OutputStream os = adbSocket.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);
			DataInputStream dis = new DataInputStream(is);
			
			JSONObject jo = JSONObject.fromObject(readStringMsg(dis));
			if(jo.getBoolean("isOk")){				
				log.info("user="+jo.getString("user"));
			}
			sendMsg(dos, getServerNotice());
			//Ȼ��ȴ�֮��Ĵ���
			addAdbConnection(adbSocket);
		} catch (IOException e) {
	
			log.error("����ADB AGENT��½����",e);
			IOUtils.closeQuietly(adbSocket);
		}
		
			
	}
	private void tryStartBridge(final Socket adbSocket,final MobileConnection mobileConnection) {
		try {
			log.info("����ֹͣ����");
			final Socket mobileSocket = mobileConnection.stopHeartBeat();
			
			log.info("��ʼ�����Ž�");
			sendMsg(new DataOutputStream(mobileSocket.getOutputStream()),genStartAdbMsg(adbSocket.getRemoteSocketAddress()));
			JSONObject jo = readJson(mobileSocket);
			if(jo.has("action")&&"heartBeat".equalsIgnoreCase(jo.getString("action"))){ //���ɶԷ��ڷ����������Ⱥ���
				jo = readJson(mobileSocket);
			}
			if(!jo.getBoolean("isOk")){
				writeConfirmResponse(adbSocket,false,jo.getString("msg")); //���ظ��ͻ���ʧ����Ϣ��Ȼ�������жϿ�����
				throw new Exception("������δ�����ADB�Žӣ�"+jo.getString("msg"));
			}
			log.info("�豸���أ�"+jo.getString("msg"));
			log.info("��ʼ�Ž�");
			StreamUtils.asynCopy(adbSocket.getInputStream(), mobileSocket.getOutputStream(),new OnCloseCallback(){
				@Override
				public void onClose() {
					log.info("�ر�adb����"+adbSocket);
					log.info("�ر��豸����"+mobileSocket);
					IOUtils.closeQuietly(adbSocket);
					releaseConnection(mobileConnection);
					
				}

				@Override
				public void onException(Throwable e) {
					log.error("�Ž�ͨѶ�쳣",e);
				}
			});
			StreamUtils.synCopy(mobileSocket.getInputStream(), adbSocket.getOutputStream());
			log.info("�Ž�����˳�");
		} catch (IOException e) {
			log.error("ͨѶ�쳣",e);
		} catch (Exception e) {
			log.error("����ADB�Ž�ʧ��",e);
		} finally{
			log.debug("�Ͽ�ADB���ӣ�"+adbSocket);
			IOUtils.closeQuietly(adbSocket);
			releaseConnection(mobileConnection);
		}
//		log.info("���ӶϿ�:" + s);

	}

	private JSONObject readJson(final Socket mobileSocket)
			throws UnsupportedEncodingException, IOException {
		return JSONObject.fromObject(readStringMsg(new DataInputStream(mobileSocket.getInputStream())));
	}
	protected void waitForAdbClient(Socket adbSocket2) {
		// TODO Auto-generated method stub
		
	}
	private void writeConfirmResponse(Socket socket,boolean isOk,String msg) throws IOException{
		JSONObject jo = new JSONObject();
		jo.put("isOk",isOk);
		jo.put("msg",msg);
		sendMsg(new DataOutputStream(socket.getOutputStream()),jo.toString());
	}
	protected String getServerNotice() {
		JSONObject jo = new JSONObject();
		jo.put("isOk",true);
		jo.put("msg","��ӭ��½��������");
		return jo.toString();
	}
	private static byte[] readMsg(DataInputStream dis) throws IOException {
		byte[] buff;
		int length = dis.readInt();
		if (length > 32767) {
			byte[] dump= new byte[64];
			int len = dis.read(dump);
			StringBuilder sb = new StringBuilder();
			for(int i=0;i<len;i++){
				sb.append(dump[i]).append(" ");
			}
			log.error("���ȳ��ޣ�"+length+"+"+sb.toString());
			throw new IllegalStateException("���ص���Ϣ���ȳ��ޣ�" + length);
		}
		buff = new byte[length];
		dis.readFully(buff);
		return buff;
	}
	private void handlerMobileSocket(final Socket s) {
		log.info("�豸�����ӣ�"+s.getRemoteSocketAddress());
		InputStream is;
		try {
			is = s.getInputStream();
			OutputStream os = s.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);
			DataInputStream dis = new DataInputStream(is);
			
			JSONObject jo = JSONObject.fromObject(readStringMsg(dis));
			if(jo.getBoolean("isOk")){		
				
			}
			sendMsg(dos, getServerNotice());
			//Ȼ��ȴ�֮��Ĵ���
			DeviceDesc desc =(DeviceDesc)JSONObject.toBean(jo.getJSONObject("device"), DeviceDesc.class);
			addMobileConnection(s,desc);
		} catch (IOException e) {
			e.printStackTrace();
			IOUtils.closeQuietly(s);
		}
		
	}
	private static void sendMsg(DataOutputStream dos, String msg)
			throws IOException {
		log.info("������Ϣ��"+msg);
		byte[] buff = msg.getBytes("UTF-8");
		dos.writeInt(buff.length);
		dos.write(buff);
	}
	private static String readStringMsg(DataInputStream dis)
			throws UnsupportedEncodingException, IOException {
		String msg = new String(readMsg(dis),"UTF-8");
		log.info("���յ���Ϣ��"+msg);
		return msg;
	}
	private synchronized void addAdbConnection(Socket s) {
		log.info("ADB AGENT�Ѽ��뵽�б�"+s.getRemoteSocketAddress());
		AdbConnection c= new AdbConnection(s);
		adbConnectionList.add(c);
		c.start();
	}
	private synchronized void addMobileConnection(Socket s,DeviceDesc desc) {
		log.info("�豸�Ѽ��뵽�б�"+s.getRemoteSocketAddress());
		MobileConnection c= new MobileConnection(s);
		c.deviceDesc = desc;
		mobileConnectionList.add(c);
		c.start();
	}
	private synchronized MobileConnection pickupConnection(String ip){
		MobileConnection result = null;
		for(MobileConnection c:this.mobileConnectionList){
			if(c.socket!=null&&((InetSocketAddress)c.socket.getRemoteSocketAddress()).getHostName().equals(ip)){
				c.isStartAdb = true; //������ֹͣ
				result = c;
				break;
			}
		}
		if(result!=null){
			this.mobileConnectionList.remove(result);
			return result;
		}
		return null; //
	}

	/**
	 * �ϵ����ӣ����豸�Լ�����������
	 * @param c
	 */
	private synchronized void releaseConnection(MobileConnection c) {
		log.info("�豸�ѶϿ����ӣ�"+c.socket.getInetAddress());
		IOUtils.closeQuietly(c.socket);
		
	}
	private synchronized void removeAdbConnection(String ip){
		for(int i=0;i<this.adbConnectionList.size();i++){
			AdbConnection c = adbConnectionList.get(i);
			if(c.socket.getInetAddress().getHostName().equals(ip)){
				log.debug("�Ƴ�ADB���ӣ�"+ip);
				if(c.isAlive()){
					c.isShuttingDown = true;
					log.debug("�Ͽ�ADB���ӣ�"+ip);
					IOUtils.closeQuietly(c.socket);
				}
				adbConnectionList.remove(i);
				return;
			}
		}
	}
	private synchronized void removeMobileConnection(String ip){
		for(int i=0;i<this.mobileConnectionList.size();i++){
			MobileConnection c = mobileConnectionList.get(i);
			if(c.socket.getInetAddress().getHostName().equals(ip)){
				log.debug("�Ƴ������豸��"+ip);
				if(c.isAlive()){
					c.isShuttingDown = true;
					log.debug("�Ͽ��豸���ӣ�"+ip);
					IOUtils.closeQuietly(c.socket);
				}
				mobileConnectionList.remove(i);
				return;
			}
		}
	}
	@Override
	public void run() {
		MobileConnection m = new MobileConnection(null);
		m.deviceDesc = new DeviceDesc();
		m.deviceDesc.band="testBand";
		m.deviceDesc.model="testModel";
		m.deviceDesc.status="tStatus";
		m.deviceDesc.tag="testTag";
		m.deviceDesc.ip="127.0.0.1";
		m.deviceDesc.version="4.4";
		this.mobileConnectionList.add(m);
		new Thread(){
			public void run(){
				adbServerRun();
			}
		}.start();
		new Thread(){
			public void run(){
				mobileServerRun();
			}
		}.start();
		
	}
	private synchronized ArrayList<DeviceDesc> getDevicesTagList(){
		ArrayList<DeviceDesc> result = new ArrayList<DeviceDesc>();
		for(MobileConnection c:mobileConnectionList){
			result.add(c.deviceDesc);
		}
		return result;
	}
	public class AdbConnection extends Thread{
		
		public void run(){
			try {
				DataOutputStream dos = new DataOutputStream(this.socket.getOutputStream());
				DataInputStream dis = new DataInputStream(this.socket.getInputStream());
				while(!isStartAdb){
					if(dis.available()<=0){
						Thread.sleep(10);
						continue;
					}
					if(isStartAdb){
						break;
					}
					JSONObject jo = JSONObject.fromObject(readStringMsg(dis)); //��������
					if(isHeartbeatResponse(jo)){ 
						sendMsg(dos,jo.toString()); //��������
					}else if("startAdb".equalsIgnoreCase(jo.getString("action"))){
						
						MobileConnection  c = pickupConnection(jo.getString("device"));
						if(c!=null){
							isStartAdb = true;
							jo = new JSONObject();
							jo.put("isOk", true);
							sendMsg(dos,jo.toString());
							tryStartBridge(this.socket,c);
							break;
						}
						//����Ҳ����豸�������Ǳ�ռ���ˣ�������Ϣ�󣬶Ͽ����ӣ������һЩ�쳣�����ϣ�����������ܹ������
						jo = new JSONObject();
						jo.put("isOk", false);
						jo.put("msg", "�Ҳ����豸�������Ѿ���ռ�ã������ԣ�");
						sendMsg(dos,jo.toString());
						releaseConnection(c);
					}else if("getDevices".equalsIgnoreCase(jo.getString("action"))){
						jo = new JSONObject();
						jo.put("isOk", true);
						JSONArray array = new JSONArray();
						array.addAll(getDevicesTagList());
						jo.put("result", array);
						sendMsg(dos,jo.toString());
						continue;//����ѭ��
					}
				}
				
			} catch (IOException e) {
				if(!isShuttingDown){
					log.error("�ͻ��������쳣��"+this.socket,e);
				}
				//���isShuttingDown ��ô�Ͳ�������
				removeAdbConnection(this.socket.getInetAddress().getHostName());;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		public AdbConnection(Socket s) {
			this.socket = s;
		}

		public Socket socket;
		private boolean isShuttingDown = false;
		private boolean isStartAdb = false;
	}
	public class MobileConnection extends Thread{
		DeviceDesc deviceDesc;
		
		public synchronized Socket stopHeartBeat(){
			isStartAdb = true;
//			if(this.isAlive()){
//				log.info("�����߳�δ�Ƴ�");
//			}
//			while(this.isAlive()){
//				try {
//					Thread.sleep(1);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			}
//			log.info("�����߳��˳�");
			return this.socket;
		}
		public void run(){
			try {
				DataOutputStream dos = new DataOutputStream(this.socket.getOutputStream());
				DataInputStream dis = new DataInputStream(this.socket.getInputStream());
				while(!isStartAdb){
					if(dis.available()<=0){
						Thread.sleep(10);
						continue;
					}
					if(isStartAdb){
						break;
					}
					JSONObject jo = JSONObject.fromObject(readStringMsg(dis)); //��������
					if(isHeartbeatResponse(jo)){ 
						sendMsg(dos,jo.toString()); //��������
					}
				}
				log.info(this.deviceDesc.tag+"����ѭ��ֹͣ");
			} catch (IOException e) {
				log.error("�ͻ��������쳣��"+this.socket,e);
				if(!isShuttingDown){
					
				}else{
					//���isShuttingDown ��ô�Ͳ�������
					removeMobileConnection(((InetSocketAddress)this.socket.getRemoteSocketAddress()).getHostName());;
				}
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		public MobileConnection(Socket s) {
			this.socket = s;
		}

		private Socket socket;
		private boolean isShuttingDown = false;
		private boolean isStartAdb = false;
	}
	private static boolean isHeartbeatResponse(JSONObject jo){
		if(jo.has("action")){
			return "heartBeat".equalsIgnoreCase(jo.getString("action"));
		}else{
			return false;
		}
		
	}
	private String genStartAdbMsg(SocketAddress socketAddress) {
		JSONObject jo = new JSONObject();
		jo.put("action","startAdb");
		jo.put("from",socketAddress.toString());
		return jo.toString();
	}
	
}
