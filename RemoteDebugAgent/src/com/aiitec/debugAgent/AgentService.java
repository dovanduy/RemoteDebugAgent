package com.aiitec.debugAgent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;




/**
 * Connect to server , report device info and accept remote debug request. 
 * 
 * @author dev Aiitec.inc
 * {@link www.aiitec.com}
 * 
 * 
 */
public class AgentService extends Service {
	public static final String SERVER_IP = "122.138.45.6";
	private static final int retryInterval = 5000;
	private static final String TAG = "AgentService";
	public static final String ACTION = "com.aiitec.debugAgent.AgentService";
	public static final String BROADCAST_CONNECTION_CHANGED = "com.aiitec.debugAgent.AgentService.CONNECTION_CHANGED",BROADCAST_SETTING_CHANGED = "com.aiitec.debugAgent.AgentService.SETTING_CHANGED";
	static Handler handler = new Handler();
	private Socket serverSocket;
	private boolean isConnected = false,isFirstConnect = true;
	private boolean hasRight;
	private BroadcastReceiver boardcastReciever;
	private int heartbeatInterval = 30000;
	private Thread serverSocketThread;
	private boolean hasNetwork;
	private String ip;

	@Override
	public IBinder onBind(Intent intent) {
		Log.v(TAG, "AgentService onBind");

		return new MsgBinder();
	}

	@Override
	public void onCreate() {
		Log.v(TAG, "AgentService onCreate");
		super.onCreate();
		observe();
		PackageManager pm = this.getPackageManager();
		hasRight = (PackageManager.PERMISSION_GRANTED) == pm.checkPermission(
				"android.permission.WRITE_SECURE_SETTINGS",
				this.getPackageName());
		IntentFilter f = new IntentFilter();
		f.addAction(BROADCAST_SETTING_CHANGED);
		f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		boardcastReciever = new BroadcastReceiver(){

			@Override
			public void onReceive(Context context, Intent intent) {
				startUpdate();
				
			}
			
		};
		this.registerReceiver(boardcastReciever, f);
	}

	public void setupNotification(Context context, String title,
			String content, String msg) {
		SharedPreferences sp = this.getSharedPreferences("remoteDebug", 0);
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(NOTIFICATION_SERVICE);
		Intent intent = new Intent(context, ControlerPanelActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 1,
				intent, Notification.FLAG_SHOW_LIGHTS
						| Notification.FLAG_ONGOING_EVENT
						| Notification.FLAG_ONLY_ALERT_ONCE
						| Notification.FLAG_NO_CLEAR
						| Notification.FLAG_FOREGROUND_SERVICE);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context);
		mBuilder.setContentTitle(title)// ����֪ͨ������
				.setContentText(content) // ����֪ͨ����ʾ����
				.setContentIntent(pendingIntent) // ����֪ͨ�������ͼ
				// .setNumber(number) //����֪ͨ���ϵ�����
				.setTicker(msg) // ֪ͨ�״γ�����֪ͨ��������������Ч����
				
				// .setWhen(System.currentTimeMillis())//֪ͨ������ʱ�䣬����֪ͨ��Ϣ����ʾ��һ����ϵͳ��ȡ����ʱ��
				// .setPriority(Notification.PRIORITY_HIGH) //���ø�֪ͨ���ȼ�
				// .setAutoCancel(true)//���������־���û��������Ϳ�����֪ͨ���Զ�ȡ��
				.setOngoing(true)// ture��������Ϊһ�����ڽ��е�֪ͨ������ͨ����������ʾһ����̨����,�û���������(�粥������)����ĳ�ַ�ʽ���ڵȴ�,���ռ���豸(��һ���ļ�����,ͬ������,������������)
				.setDefaults(sp.getBoolean("isShakeEnable",true)?Notification.DEFAULT_VIBRATE:Notification.DEFAULT_LIGHTS)// ��֪ͨ������������ƺ���Ч������򵥡���һ�µķ�ʽ��ʹ�õ�ǰ���û�Ĭ�����ã�ʹ��defaults���ԣ��������
				// Notification.DEFAULT_ALL Notification.DEFAULT_SOUND ������� //
				// requires VIBRATE permission
				.setSmallIcon(R.drawable.ic_launcher);// ����֪ͨСICON
		
		mNotificationManager.notify(1, mBuilder.build());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.unregisterReceiver(this.boardcastReciever);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAG, "AgentService onStartCommand");
		return super.onStartCommand(intent, flags, startId);
	}

	private void connectServer() {

		try {
			serverSocket.setSoTimeout(0);
			serverSocket.connect(getServerAddress(),5000);
			
			Log.d(TAG, "����������������ӣ�"+serverSocket.getRemoteSocketAddress());
			InputStream is = serverSocket.getInputStream();
			OutputStream os = serverSocket.getOutputStream();
			DataOutputStream dos = new DataOutputStream(os);
			DataInputStream dis = new DataInputStream(is);

			String msg = getReportMsg();
			sendString(dos, msg);

			msg = readMsg(dis);
			
			JSONObject jo = new JSONObject(msg);
			boolean isOk = jo.getBoolean("isOk");
			msg = jo.getString("msg");

			if (!isConnected) { // �����ǰδ�����������ӳɹ��㲥
				this.isConnected = true;
				this.sendBroadcast(new Intent(BROADCAST_CONNECTION_CHANGED)
						.putExtra("isOk", isOk).putExtra("msg", msg));

			} else { // �����ǰ�Ѿ����ӳɹ���˵���ܿ����ǶϿ�����
				Log.i(TAG, this.toast("Զ�̵������������ӵ�������"));
				handler.postDelayed(new Runnable(){
					public void run(){
						sendConnectionChangedBoardcast(true);
					}
				},1000);
				
			}
			// ��ʼ�ȴ���λ�����Ե�����
			heartbeatForWaiting(this.serverSocket,dis,dos); //�����ȴ����ӣ����������������һֱ�ȴ�
			//�����������Ϣ��ʼ������������̣�������κ��쳣�������������ȥ��������Ӿͻ�رգ�ֻ������

			// ��λ��
			SharedPreferences sp = this.getSharedPreferences("remoteDebug", 0); 
			boolean isRemoteDebugEnable = sp.getBoolean("remoteDebugEnable",
					false);
			if (!isRemoteDebugEnable) { // ���δ���ã��򷵻�δ������Ϣ�����Ͽ����ӣ�Ϊ�˷�ֹͨѶ�쳣���������ӣ�
				Log.i(TAG, "δ����Զ�̵���");
				jo = new JSONObject();
				jo.put("isOk", false);
				jo.put("msg", "δ����Զ�̵���");
				this.sendString(dos, jo.toString());
				dos.flush();
				return;
			}
			jo = new JSONObject();
			jo.put("isOk", true);
			jo.put("msg", "�Ѿ�׼�������ȴ�ADB����");
			this.sendString(dos, jo.toString());
			
			serverSocket.setSoTimeout(0); //֮����£��Ϳ��Ʋ��ˣ���ԭΪ����ʱ�ĳ�����
			sendConnectionChangedBoardcast(true);

		} catch (IOException e) {
			if(!this.isConnected&&isFirstConnect){ //���δ���ӣ�Ӧ�ü�������ʾ
				Log.e(TAG, this.toastLong("�����������ͨѶ����"), e);
			}else{ //�����
				Log.d(TAG,"�����������ͨѶ����",e);
			}
		} catch (JSONException e1) {
			Log.e(TAG, this.toastLong("JSON��ʽ��"), e1);
		} finally {
			IOUtils.closeQuietly(this.serverSocket);
			isFirstConnect = false;
			this.sendConnectionChangedBoardcast(false);
		}
	}

	private void heartbeatForWaiting(Socket socket,DataInputStream dis, DataOutputStream dos) throws IOException {
		JSONObject jo = null;
		long time;
		try {
			while(true){
				jo = new JSONObject();
				time = System.currentTimeMillis();
//				jo.put("action", "heartBeat");
//				jo.put("time", time);
//				Log.d(TAG, "������������");
//				this.sendString(dos, jo.toString()); //��������
//				Log.d(TAG, "������������");
				jo = new JSONObject(this.readMsg(dis));  //����һ����Ϣ�������Ϣ�п��ܲ�һ�����������ر���
				if(isHeartbeatResponse(jo, time)){ //��������������
					
					while (dis.available() <= 0) {
						if(socket.isClosed()){
							Log.i(TAG, "���ӹرգ���������ֹͣ");
							IOUtils.closeQuietly(dis);
							IOUtils.closeQuietly(dos);
							return;
						}
						Thread.sleep(10);
						if(System.currentTimeMillis()-time>this.heartbeatInterval){
							break;
						}
					}
					
					if(dis.available()<=0){ //û�����ݵĻ�������������������,���¿�ʼѭ��
						continue;
					}
					//��������Ӧ���������ݣ���ʼ�����������					
				}
//				String msg = this.readMsg(dis);
//				if(!this.checkConnctionMsg(msg)){
//					throw new RuntimeException("�޷�ʶ�����Է�����������");
//				}
				return; //����֮�󣬾Ϳ�ʼ�Ž�ADB��
			}
			
			
		} catch (JSONException e) {
			throw new RuntimeException("json����",e);
		} catch (IOException e) {
			throw new IOException("���������ͳ���",e);
		} catch (InterruptedException e) {
			throw new RuntimeException("�������̱߳����",e);
		}
		
	}

	private boolean isHeartbeatResponse(JSONObject jo, long time)
			throws JSONException {
		return "heartBeat".equalsIgnoreCase(jo.getString("action"))&&time==jo.getLong("time");
	}

	private void sendString(DataOutputStream dos, String msg)
			throws  IOException {
		Log.d(TAG, "������Ϣ��"+msg);
		byte[] buff = msg.getBytes("UTF-8");
		dos.writeInt(buff.length);
		dos.write(buff);
	}

	private InetSocketAddress getServerAddress() {
		return new InetSocketAddress(SERVER_IP, 5556);//
	}




	private String readMsg(DataInputStream dis) throws IOException {
		byte[] buff;
		int length ;
		try{
			length = dis.readInt();
		}catch(SocketException e){
			if(e.getMessage().contains("ECONNREST")){
				length = dis.readInt();
			}else{
				throw e;
			}
		}
		if (length > 32767) {
			throw new IllegalStateException("���ص���Ϣ���ȳ��ޣ�" + length);
		}
		buff = new byte[length];
		dis.readFully(buff);
		String msg = new String(buff, "UTF-8");
		Log.d(TAG, "������Ϣ��"+msg);;
		return msg;
	}

	private String getReportMsg() throws JSONException {
		JSONObject jo = new JSONObject();
		jo.put("isOk", true);
		DeviceDesc dd = new DeviceDesc();
		JSONObject device = new JSONObject();
		
		dd.setTag(android.os.Build.SERIAL);
		dd.setBand(android.os.Build.BRAND);
		dd.setModel(android.os.Build.MODEL);
		dd.setStatus("����");
		dd.setVersion(android.os.Build.VERSION.RELEASE);
		dd.setIp(this.ip);
		device.put("tag", dd.getTag());
		device.put("band", dd.getBand());
		device.put("model", dd.getModel());
		device.put("status", dd.getStatus());
		device.put("ip", dd.getIp());
		device.put("version",dd.getVersion());
		
		
		jo.put("device", device);
		
		
		return jo.toString();
	}


	private void asynCopy(final InputStream is, final OutputStream os) {
		new Thread() {
			public void run() {
				try {
					copyLarge(is, os);
				} catch (IOException e) {
					IOUtils.closeQuietly(is);
					IOUtils.closeQuietly(os);
				} finally {
					try {
						Thread.sleep(200);// ����ʱ�䷴Ӧ
					} catch (InterruptedException e) {
					}

				}
			}
		}.start();
	}

	private static long copyLarge(InputStream input, OutputStream output)
			throws IOException {
		byte[] buffer = new byte[32768];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	private int execCommand(String... commands) throws IOException {
		Log.i("TAT", "ִ��ָ��");
		for (String str : commands) {
			Log.i(TAG, str);
		}
		Runtime runtime = Runtime.getRuntime();
		Process proc = runtime.exec(commands);
		try {
			proc.waitFor();
		} catch (InterruptedException e) {
			Log.e(TAG,"���̵ȴ������",e);
		}
		return proc.exitValue();
	}

	public void suExec(String str) {
		try {
			// Ȩ������
			Process p = Runtime.getRuntime().exec("su");
			// ��ȡ�����
			OutputStream outputStream = p.getOutputStream();
			DataOutputStream dataOutputStream = new DataOutputStream(
					outputStream);
			// ������д��
			dataOutputStream.writeBytes(str);
			// �ύ����
			dataOutputStream.flush();
			// �ر�������
			dataOutputStream.close();
			outputStream.close();
		} catch (Throwable t) {
			throw new RuntimeException("ִ��SU����ʧ��");
		}
	}

	private String execCommandForResult(String command) throws IOException {
		// start the ls command running
		// String[] args = new String[]{"sh", "-c", command};
		Runtime runtime = Runtime.getRuntime();
		Process proc = runtime.exec(command); // ��仰����shell��߼����Լ�ĵ���
		// ����в����Ļ�����������һ�������ص�exec����
		// ʵ��������ִ��ʱ������һ���ӽ���,��û�и����̵Ŀ���̨
		// Ҳ�Ϳ��������,����������Ҫ����������õ�shellִ�к�����
		InputStream inputstream = proc.getInputStream();
		InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
		BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
		// read the ls output
		String retMsg = "";
		String line = "";
		StringBuilder sb = new StringBuilder(line);
		while ((line = bufferedreader.readLine()) != null) {
			if (command.equals(line)) {
				continue;
			}
			sb.append(line);
			sb.append('\n');
		}
		// tv.setText(sb.toString());
		// ʹ��execִ�в����ִ�гɹ��Ժ�ŷ���,������������
		// ������ĳЩ������Ǻ�Ҫ����(���縴���ļ���ʱ��)
		// ʹ��wairFor()���Եȴ�����ִ������Ժ�ŷ���
		try {
			proc.waitFor();
			retMsg = "exit value = " + proc.exitValue();
			Log.i(TAG, retMsg);
		} catch (InterruptedException e) {
			System.err.println(e);
		}
		String result = sb.toString();
		Log.d(TAG, result);
		toast(retMsg, result);
		return result;
	}

	private String toast(final String msg) {
		handler.post(new Runnable() {
			public void run() {
				Toast.makeText(AgentService.this, msg, Toast.LENGTH_SHORT)
						.show();
			}
		});
		return msg;
	}

	private String toastLong(final String msg) {
		handler.post(new Runnable() {
			public void run() {
				Toast.makeText(AgentService.this, msg, Toast.LENGTH_LONG)
						.show();
			}
		});
		return msg;

	}

	private void toast(final String retMsg, final String result) {
		toast(retMsg + "\n" + result);

	}

	public class MsgBinder extends Binder {
		/**
		 * ��ȡ��ǰService��ʵ��
		 * 
		 * @return
		 */
		public AgentService getService() {
			return AgentService.this;
		}

	}

	private void observe() {
		ContentResolver resolver = this.getContentResolver();
		resolver.registerContentObserver(Settings.Secure
				.getUriFor(android.provider.Settings.Global.ADB_ENABLED),
				false, new ContentObserver(null) {
					@Override
					public void onChange(boolean selfChange) {
						startUpdate();
					}
				});
	}
	private void startUpdate() {
		update();

	}

	private String intToip(int i) {
		return (i & 0xFF) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff)
				+ "." + ((i >> 24) & 0xff);
	}
	private void update() {
		Log.i(TAG, "����״̬");
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
		if (!wifiManager.isWifiEnabled()) {
			ip = "δ����WIFI";
		} else {
			ip = intToip(wifiManager.getConnectionInfo().getIpAddress());
		}
		hasRight = (PackageManager.PERMISSION_GRANTED) == this.getPackageManager().checkPermission(
				"android.permission.WRITE_SECURE_SETTINGS",
				this.getPackageName());
		ContentResolver resolver = this.getContentResolver();
		boolean mAdbEnabled = Settings.Secure.getInt(resolver,
				android.provider.Settings.Global.ADB_ENABLED, 0) != 0;
		
		if(!mAdbEnabled){
			this.closeServerConnection();
			setupNotification(this, "Զ�̵���AGENT", "������رգ����ȿ���'USB����'",
					this.toastLong("Զ�̵���AGENT�رգ����ȿ���'USB����'"));
			return;
		}
		//����Ƿ�WIFI���磬����Ͽ�����
		ConnectivityManager c = (ConnectivityManager) AgentService.this
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = c.getActiveNetworkInfo();
		if(info==null){
			this.hasNetwork = false;
			this.closeServerConnection();
			setupNotification(this, "Զ�̵���AGENT", "������رգ�����ͨ��WIFI���ӵ�����",
					this.toastLong("Զ�̵���AGENT����رգ�����ͨ��WIFI���ӵ�����"));
			return;
		}else if(info.getType() != ConnectivityManager.TYPE_WIFI){
			this.closeServerConnection();
			if(this.isOccupiedPort()){ //
				if(!disableDebug()){ //����޷��ڷ�ʱ��ر�����ADB������ʾ�û��ر�ADB����ֹ����������������
					this.toastLong("Զ�̵���AGENTû��Ȩ�޹ر�����ADB��Ϊ�˱�����յ������������Ӻ͹���������عر�ADB");
					Intent intent = new Intent(
							Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
					return;
				}
			}else{
				//˵��ֻ��û��������ѣ��Ѿ�û������ADB��������Ǿ����߰�
				this.hasNetwork = false;
				this.closeServerConnection();
				return;
			}
		}
		this.hasNetwork = true;
		//���濪ʼ������WIFI�������ӵ����
		SharedPreferences sp = getSharedPreferences("remoteDebug", 0);
		boolean isRemoteDebugEnable = sp.getBoolean("remoteDebugEnable",true);
		if((!isRemoteDebugEnable)&&this.isServerConnecting()){ //�����Ӧ���������Ӷ���������������ֹ
			this.closeServerConnection();
			setupNotification(this, "Զ�̵���AGENT", "�Ѵӷ������Ͽ��������Ҫ������������",
					this.toastLong("Զ�̵���AGENT�Ѵӷ������Ͽ��������Ҫ������������"));
			return;
		}else if(isRemoteDebugEnable&&this.isServerConnecting()&&!mAdbEnabled){ //������״̬�¹ر�ADB
			this.closeServerConnection();
			setupNotification(this, "Զ�̵���AGENT", "�Ѵӷ������Ͽ��������Ҫ������������",
					this.toastLong("Զ�̵���AGENT�Ѵӷ������Ͽ��������Ҫ������������"));
			return;
		}else if((!isRemoteDebugEnable)&&(!this.isServerConnecting())){ //�����Ӧ�������������Ϣ��ʾ��������
			setupNotification(this, "Զ�̵���AGENT", "δ���ӵ������������������п���",
					this.toastLong("Զ�̵���AGENTδ���ӵ������������������п���"));
			sendConnectionChangedBoardcast(false);
			return;
		}else if(isRemoteDebugEnable&&!this.isServerConnecting()){ //���Ӧ���������������Ӷ�δ������������
			try{
				
				String port = sp.getString("adbdPort", "5555"); // ����˿�Ϊ0 ���������USB����
				try {
					if (this.hasRight) { // �����Ȩ�ޣ����Լ����ö˿ں���������,ʵ�����˹�����
						disableDebug();
						suExec("setprop service.adb.tcp.port " + port);
						Settings.Secure.putInt(getContentResolver(),
								android.provider.Settings.Global.ADB_ENABLED, 1); //����ADB
						startConnectServer(100); //��ʼ��ط���������
					} else {// ���û��Ȩ�ޣ������Ƿ��Ѿ�����ص�ADB TCPIP �������û���򵯳���ص���˾��ʾ��������Ĭ�Ϸ�ʽ�������ȴ��û��ֹ�����
						if(!isOccupiedPort()){
							setupNotification(this, "Զ�̵���AGENT", "ADB�ѿ�������ȷ���Ѿ�ִ��'ADB TCPIP "+ port,
									this.toastLong("��ȷ���ֻ��Ѿ�ִ��'ADB TCPIP "+ port
											+ "'��������λ���޷����ӵ����豸"));
							
						}else{
							setupNotification(this, "Զ�̵���AGENT", "������Զ�̵��Թ��ܣ������������",
									this.toastLong("������Զ�̵��Թ��ܣ����Խ������Է������Ĳ���ָ��"));
						}
						startConnectServer(1);
						
					}
				} catch (Throwable e) {
					Log.e(TAG, "����ADB������ȷ���ֻ��Ѿ���root", e);
					throw new Exception("����ADB������ȷ���ֻ��Ѿ���root");
				}
			}catch(Throwable e){
				setupNotification(this, "Զ�̵���AGENT", "����Զ�̵��Է������", 
						this.toastLong("Զ�̵���AGENT����Զ�̵��Է������"));
			}
		}else{
			//ʲô�����ø�
			Log.e(TAG, "�䵽�����֧");
			this.sendConnectionChangedBoardcast(this.isServerConnected());
//			this.closeServerConnection();
		}
	}

	public boolean isHasNetwork() {
		return hasNetwork;
	}

	/**
	 * 
	 * @return
	 */
	private boolean disableDebug()  {

		boolean enableAdb = (Settings.Secure.getInt(getContentResolver(),
				android.provider.Settings.Global.ADB_ENABLED, 0) > 0);
		if (enableAdb && this.hasRight) {
			Settings.Secure.putInt(getContentResolver(),
					android.provider.Settings.Global.ADB_ENABLED, 0);
			this.closeServerConnection();
			return true;
		} else if (enableAdb) {
			this.toast("û��Ȩ�޹ر�ADB,����Ҫ����ADB���뵽���������ý���������");
		}
		
		return false;
	}

	public void closeServerConnection() {
		if(this.serverSocket==null&&serverSocketThread == null){
			sendConnectionChangedBoardcast(false);
			return;
		}
		if(this.serverSocket!=null&&!this.serverSocket.isClosed()){
			try{
//				IOUtils.closeQuietly(this.serverSocket.getInputStream());
//				IOUtils.closeQuietly(this.serverSocket.getOutputStream());
				this.serverSocket.close();
			}catch(Throwable e){
				e.printStackTrace();
			}
		}
		
//		IOUtils.closeQuietly(this.serverSocket);
		if(this.serverSocket!=null&&!this.serverSocket.isClosed()){
			Log.e(TAG, "�ز���Socket��");
			serverSocketThread.interrupt();
			IOUtils.closeQuietly(this.serverSocket);
		}
		this.serverSocket = null;
		long startTime = System.currentTimeMillis();
		try {
			while(serverSocketThread.isAlive()){
				
				if(System.currentTimeMillis()-startTime>10000){
					throw new RuntimeException("�ر�ʧ��");
				}
				Thread.sleep(10);
			}
		} catch (InterruptedException e) {
			
		}
		this.serverSocket = null;
		Log.i(TAG, "��������������ѶϿ�");
		this.toast("��������������ѶϿ�");
		
		
		serverSocketThread = null;
		sendConnectionChangedBoardcast(false);
	}
	


//	public void enableRemoteDebug() throws Exception {
//		
//		String port = getPortFromPref();
//		try {
//			if (this.hasRight) { // �����Ȩ�ޣ����Լ����ö˿ں���������
//				
//				suExec("setprop service.adb.tcp.port " + port);
//				Settings.Secure.putInt(getContentResolver(),
//						android.provider.Settings.Global.ADB_ENABLED, 1);
//				startConnectServer(100);
//
//			} else {// ���û��Ȩ�ޣ��򵯳���ص���˾��ʾ��������Ĭ�Ϸ�ʽ����
//				this.toastLong("��ȷ���ֻ��Ѿ�ִ��'ADB TCPIP "+ port
//						+ "'��������λ���޷����ӵ����豸");
//				startConnectServer(1);
//			}
//		} catch (Throwable e) {
//			Log.e(TAG, "����ADB������ȷ���ֻ��Ѿ���root", e);
//			throw new Exception("����ADB������ȷ���ֻ��Ѿ���root");
//		}
//	}
	
	

	public void startConnectServer(final int delay) {
		if(this.serverSocket!=null||serverSocketThread!=null){
			throw new RuntimeException("���ȶϿ�����������"); //һ����ĳЩ�ط�����״̬����
		}
		this.serverSocket = new Socket();
		serverSocketThread = new Thread() {
			public void run() {
				try {
					this.setName("startConnectServer");
					Thread.sleep(delay);
					isFirstConnect = true;
					while(recreateSocket()){
						// ��λ��
						SharedPreferences sp = getSharedPreferences("remoteDebug", 0);
						boolean shouldConnectServer = sp.getBoolean("remoteDebugEnable",
								true);
						if(!shouldConnectServer){
							break;
						}
						this.setName("connectServer");
						connectServer();
						if(serverSocket!=null&&!serverSocket.isClosed()){
							Thread.sleep(retryInterval);//����15�������
						}
						
					}
				} catch( Throwable e){
					Log.e(TAG, "����������ӳ���",e);
				}finally{
					AgentService.this.isConnected = false;
					isFirstConnect = true;
					sendConnectionChangedBoardcast(false);
//					closeServerConnection();
				}

			}
		};
		serverSocketThread.start();
	}
	/**
	 * �ٴδ���serverSocket
	 * @return
	 */
	private boolean recreateSocket(){
		if(serverSocket!=null){
			serverSocket = new Socket();
			return true;
		}
		return false;
	}

	/**
	 * �жϷ������Ƿ��������ӣ��������Ӳ�����һ������Զ�̵��ԣ����Խ�����ʾ�ֻ����������ڶ��ѣ�
	 * 
	 * @return
	 */
	public synchronized boolean isServerConnecting() {
		return (this.serverSocket != null&&this.serverSocketThread!=null);
	}
	
	public synchronized boolean isServerConnected() {
		return (this.serverSocket != null&&this.serverSocket.isConnected());
	}

	/**
	 * �ж��Ƿ�������Զ�̵��ԣ������˲���������ʹ�ã����Խ�����ʾ����
	 * 
	 * @return
	 */
	public boolean isRemoteDebugEnable() {
		SharedPreferences sp = this.getSharedPreferences("remoteDebug", 0);
		return sp.getBoolean("remoteDebugEnable", true);
	}

	public boolean isRemoteDebuging() {
		return false;
	}
	
	private synchronized  boolean isOccupiedPort(){
		String port = getPortFromPref();
		try {
			ServerSocket server = new ServerSocket(Integer.parseInt(port));			
			IOUtils.closeQuietly(server);
		} catch (NumberFormatException e) {
			throw new RuntimeException("�˿���Ϣ��",e);
		} catch (IOException e) {
			return true;
		}
		return false;
	}

	private String getPortFromPref() {
		SharedPreferences sp = this.getSharedPreferences("remoteDebug", 0);
		String port = sp.getString("adbdPort", "4555");
		return port;
	}

	private String getPid() throws IOException {
		String msg = execCommandForResult("ps | grep adbd");
		BufferedReader reader = new BufferedReader(new StringReader(msg));
		String line = reader.readLine();

		if (line.startsWith("USER")) {
			line = reader.readLine();
		}
		reader.close();
		Matcher m = Pattern.compile("\\d+").matcher(line);
		if (!m.find()) {
			return null;
		} else {
			return m.group();
		}
	}

	private void restartRemoteDebug(String port) throws IOException {

		suExec("stop adbd");
		suExec("setprop service.adb.tcp.port " + port);
		suExec("start adbd ");
		// android.os.Process.killProcess(pid);

	}

	private void startRemoteDebug(String port) {
		suExec("setprop service.adb.tcp.port " + port);
		suExec("start adbd ");
	}

	private void alert(final String msg) {
		handler.post(new Runnable() {
			public void run() {
				new AlertDialog.Builder(AgentService.this).setTitle(msg)
						.setPositiveButton("ȷ��", null).create().show();
			}
		});

	}

	private void alertAndLog(String msg) {
		this.alertAndLog(msg, null);
	}

	private void alertAndLog(String msg, Throwable e) {
		Log.e(TAG, msg, e);
		alert(msg);
	}

	private void sendConnectionChangedBoardcast(boolean isOk) {
		sendBroadcast(new Intent(BROADCAST_CONNECTION_CHANGED).putExtra("isOk", isOk));
	}
}
