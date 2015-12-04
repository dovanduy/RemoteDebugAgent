package com.aiitec.debugAgent;

import java.net.ServerSocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.widget.Toast;

/**
 * 
 * @author dev Aiitec.inc
 * {@link www.aiitec.com}
 *
 */
public class SettingFragment extends PreferenceFragment {
	private static final String TAG = "SettingFragment";
	Handler handler = new Handler();
	private String adbPort, ip;
	private EditTextPreference editTextPref;
	private AgentService msgService;
	ServiceConnection conn = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			msgService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// ����һ��MsgService����
			Log.i(TAG, "onServiceConnected:ServiceConnection");
			msgService = ((AgentService.MsgBinder) service).getService();
			

		}
	};
	private CheckBoxPreference checkboxDebugEnable;
	private CheckBoxPreference checkboxRemoteDebugEnable;
	private BroadcastReceiver receiver;
	private boolean hasRight;
	private CheckBoxPreference checkboxInvokedable;
	private EditTextPreference prefServerIp;
	private EditTextPreference prefServerPort;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setPreferenceScreen(createPreferenceScreen());

	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		Intent intent = new Intent(AgentService.ACTION);
		activity.startService(intent);
		
		activity.bindService(intent, conn, Context.BIND_AUTO_CREATE);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(AgentService.BROADCAST_CONNECTION_CHANGED);
		receiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				
				String action = intent.getAction();
				if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					Log.d(TAG, "����״̬�ı�");
					ConnectivityManager c = (ConnectivityManager) getActivity()
							.getSystemService(Context.CONNECTIVITY_SERVICE);
					NetworkInfo info = c.getActiveNetworkInfo();
					if(info==null){
						editTextPref.setSummary("δ�ܻ�ȡ����������Ϣ");
					}else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
						updateIp(adbPort);
					}

				}else if(action.equals(AgentService.BROADCAST_CONNECTION_CHANGED)){
					handler.post(new Runnable(){
						public void run(){
							update() ;
							checkboxDebugEnable.setEnabled(true);
//							updateRemoteCheckBox(intent.getBooleanExtra("isOk", true));
						}
					});
					
				}

			}
		};
		activity.registerReceiver(receiver, filter);
		//�ж��Ƿ����޸ĵ�Ȩ��
		PackageManager pm = this.getActivity().getPackageManager();
		hasRight = (PackageManager.PERMISSION_GRANTED)==pm.checkPermission("android.permission.WRITE_SECURE_SETTINGS", this.getActivity().getPackageName());

		
				
	}

	@Override
	public void onStart() {
		super.onStart();
		
		observe() ;
		if(!hasRight){
//			checkboxDebugEnable.setEnabled(false);
			checkboxDebugEnable.setSummary("δ���ϵͳȨ�޵��豸��Ҫ��ת��'������Աѡ��'������");
			checkboxInvokedable.setEnabled(false);
			checkboxInvokedable.setSummary("δ���ϵͳȨ�޵��豸���ܱ����������ͣADB");
		}
		handler.postDelayed(new Runnable(){
			public void run(){
				refreshRight();
				update();					
			}
		},1000);
//		handler.post(new Runnable(){
//			public void run(){
//
//				
//			}
//		});
//		update();
	}

	public void refreshRight() {
		try{
			this.msgService.suExec("");
			this.hasRight = true;
			this.update();
			checkboxInvokedable.setSummary("����δ���û�ȷ�Ͼ��ܱ�Զ������Զ�̵���");
			checkboxInvokedable.setEnabled(false);//��ʱ����
		}catch(Throwable e){
			checkboxInvokedable.setSummary("δ���ϵͳȨ�޵��豸���ܱ����������ͣADB");
			checkboxInvokedable.setEnabled(false);
			Log.d(TAG, "��ȡȨ��ʧ��",e);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.getActivity().unbindService(this.conn);
		this.getActivity().unregisterReceiver(receiver);
	}

	private PreferenceScreen createPreferenceScreen() {
		getPreferenceManager().setSharedPreferencesName("remoteDebug");
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(
				this.getActivity());
		// Inline preferences
		PreferenceCategory inlinePrefCat1 = new PreferenceCategory(
				this.getActivity());
		inlinePrefCat1.setTitle("��������");
		root.addPreference(inlinePrefCat1);
		
		checkboxDebugEnable = new CheckBoxPreference(
				this.getActivity());
		checkboxDebugEnable.setTitle("����ADB������USB���ԣ�");
		checkboxDebugEnable.setSummary("δ����");
		inlinePrefCat1.addPreference(checkboxDebugEnable);
		checkboxDebugEnable.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				
				if(hasRight){
					try{
						Settings.Secure.putInt(getActivity().getContentResolver(),
								android.provider.Settings.Global.ADB_ENABLED, newValue.toString().equalsIgnoreCase("TRUE")?1:0);
						checkboxDebugEnable.setEnabled(false);
					}catch(Throwable e){
						Log.i(TAG, "����ADB����",e);
						SettingFragment.this.update();
					}
					
					sendSettingChangedBoradcast();
					
					return true;
				}
				if(newValue.toString().equalsIgnoreCase("TRUE")){
					toastLong("û��Ȩ������ADB���뵽������Ա���ý������ù�ѡ'USB����'");					
				}else{
					toastLong("û��Ȩ��ֹͣADB���뵽������Ա���ý�������ȡ����ѡ'USB����'");
				}
				Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
				startActivity(intent);
				return false;
				
			}
		});
		
		CheckBoxPreference checkboxShakge = new CheckBoxPreference(
				this.getActivity());
		checkboxShakge.setTitle("֪ͨ��Ϣ����");
		checkboxShakge.setKey("isShakeEnable");
		checkboxShakge.setDefaultValue(true);
		checkboxShakge.setSummary("�������ص�֪ͨ��������");
		inlinePrefCat1.addPreference(checkboxShakge);
		
		
		PreferenceCategory inlinePrefCat = new PreferenceCategory(
				this.getActivity());
		inlinePrefCat.setTitle("Զ�̵���");
		root.addPreference(inlinePrefCat);
		
		
		checkboxRemoteDebugEnable = new CheckBoxPreference(
				this.getActivity());
		checkboxRemoteDebugEnable.setKey("remoteDebugEnable");
		checkboxRemoteDebugEnable.setTitle("ʹ��Զ�̵���");
		checkboxRemoteDebugEnable.setSummary("δ����");
		inlinePrefCat.addPreference(checkboxRemoteDebugEnable);
		checkboxRemoteDebugEnable.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			@Override
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				if(newValue.toString().equalsIgnoreCase("TRUE")){
					checkboxRemoteDebugEnable.setEnabled(false);
					checkboxRemoteDebugEnable.setSummary("�����С���");
					SettingFragment.this.asynStartRemoteDebug();
				}else{
					checkboxRemoteDebugEnable.setEnabled(false);
					checkboxRemoteDebugEnable.setSummary("�����С���");
					sendSettingChangedBoradcast();
				}
				return true;
			}
		});

		checkboxInvokedable = new CheckBoxPreference(this.getActivity());
		checkboxInvokedable.setKey("adbdInvokedable");
		checkboxInvokedable.setTitle("�������绽��Զ�̵���");
		checkboxInvokedable.setSummary("����δ���û�ȷ�Ͼ��ܱ�Զ������Զ�̵���");
		checkboxInvokedable.setChecked(true);
		inlinePrefCat.addPreference(checkboxInvokedable);

		SharedPreferences sp = inlinePrefCat.getPreferenceManager()
				.getSharedPreferences();
		adbPort = sp.getString("adbdPort", "4555");
		editTextPref = new EditTextPreference(this.getActivity());
		editTextPref.setDialogTitle("ADBD�˿�");
		editTextPref.setKey("adbdPort");
		editTextPref.setTitle("ADBD�˿�");
		editTextPref.getEditText().setInputType(
				InputType.TYPE_NUMBER_FLAG_DECIMAL);
		editTextPref
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						if (newValue == null) {
							return false;
						}
						if (!newValue.toString().matches("\\d{4,5}")) {
							alert("������1001~65534��Χ�Ķ˿ں�");
							return false;
						}
						int x = Integer.parseInt(newValue.toString());
						if (x > 1000 && x < 65535) {
							adbPort = newValue.toString();
							updateIp(adbPort);
							sendSettingChangedBoradcast();
							return true;
						} else {
							alert("������1001~65534��Χ�Ķ˿ں�");
							return false;
						}

					}
				});
		updateIp(sp.getString("adbdPort", "4555"));
		inlinePrefCat.addPreference(editTextPref);
		
		prefServerIp = new EditTextPreference(this.getActivity());
		prefServerIp.setDialogTitle("������IP");
		prefServerIp.setKey("serverIp");
		prefServerIp.setDefaultValue(AgentService.SERVER_IP);
		prefServerIp.setTitle("������IP");
		prefServerIp.getEditText().setInputType(
				InputType.TYPE_CLASS_TEXT);
		prefServerIp
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						if (newValue == null) {
							return false;
						}
						if (!newValue.toString().matches("[0-9a-zA-Z._-]{3,64}")) {
							alert("��������ȷ��URL����IP��ʽ��ʽ");
							return false;
						}
						updateServerIp(newValue.toString());
						sendSettingChangedBoradcast();
						return true;
					}
				});
		this.updateServerIp(sp.getString("serverIp", AgentService.SERVER_IP));
		inlinePrefCat.addPreference(prefServerIp);
		
		prefServerPort = new EditTextPreference(this.getActivity());
		prefServerPort.setDialogTitle("�������˿�");
		prefServerPort.setDefaultValue("4555");
		prefServerPort.setKey("serverPort");
		prefServerPort.setTitle("�������˿�");
		prefServerPort.getEditText().setInputType(
				InputType.TYPE_CLASS_NUMBER);
		prefServerPort
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						if (newValue == null) {
							return false;
						}
						if (!newValue.toString().matches("\\d{4,5}")) {
							alert("������1001~65534��Χ�Ķ˿ں�");
							return false;
						}
						int x = Integer.parseInt(newValue.toString());
						if (x > 1000 && x < 65535) {
							updateServerPort(newValue.toString());
							sendSettingChangedBoradcast();
							return true;
						} else {
							alert("������1001~65534��Χ�Ķ˿ں�");
							return false;
						}

					}
				});

		this.updateServerPort(sp.getString("serverPort", "4555"));
		inlinePrefCat.addPreference(prefServerPort);
		
		return root;
	}

	protected void sendSettingChangedBoradcast() {
		Log.i(TAG, "�����˹㲥BROADCAST_SETTING_CHANGED");
		getActivity().sendBroadcast(new Intent(AgentService.BROADCAST_SETTING_CHANGED));
	}

	private void observe() {
		ContentResolver resolver = this.getActivity().getContentResolver();
		resolver.registerContentObserver(Settings.Secure
				.getUriFor(android.provider.Settings.Global.ADB_ENABLED),
				false, new ContentObserver(handler) {
					@Override
					public void onChange(boolean selfChange) {
						update();
						
					}
				});
	}
	  
	
	  
	private   void  update() {  
	    ContentResolver resolver = this.getActivity().getContentResolver();  
	    boolean mAdbEnabled = Settings.Secure.getInt(resolver,  
	    		android.provider.Settings.Global.ADB_ENABLED, 0 ) !=  0 ;
	   
	    String desc;
	    if(this.hasRight){
	    	desc = mAdbEnabled?"������":"δ����";
	    }else{
	    	desc = mAdbEnabled?"������":"δ�������������뿪����Աѡ��������USB����";
	    }
	    checkboxDebugEnable.setChecked(mAdbEnabled);
	    checkboxDebugEnable.setSummary(desc);
	    checkboxDebugEnable.setEnabled(true);
	    checkboxRemoteDebugEnable.setEnabled(true);
		if(this.msgService.isServerConnected()&&checkboxRemoteDebugEnable.isChecked()&&mAdbEnabled){
			checkboxRemoteDebugEnable.setSummary("�Ѿ����ӵ������������Կ�ʼԶ�̵���");
		}else if(checkboxRemoteDebugEnable.isChecked()&&!mAdbEnabled){
			checkboxRemoteDebugEnable.setSummary("δ����ADB��Զ�̵���δ����");
		}else if(mAdbEnabled&&checkboxRemoteDebugEnable.isChecked()&&this.msgService.isServerConnecting()){
			checkboxRemoteDebugEnable.setSummary("���ڳ������ӷ���������");
		}else if(checkboxRemoteDebugEnable.isChecked()&&this.msgService.isServerConnecting()&&!this.msgService.isServerConnected()){
			checkboxRemoteDebugEnable.setSummary("����������Ӧ��Ҳ��û������ADB");
		}else if(checkboxRemoteDebugEnable.isChecked()&&!this.msgService.isHasNetwork()){
			checkboxRemoteDebugEnable.setSummary("û�����磬Զ�̵��Բ�����");
		}else{
			checkboxRemoteDebugEnable.setSummary("δ����");
		}
//	    updateRemoteCheckBox(this.msgService.isServerConnected());
	}  
	private void updateServerIp(String ip) {
		prefServerIp.setSummary(ip);
//		prefServerIp.setEnabled(checkboxRemoteDebugEnable.isChecked()); //�����û�������ǰ�޸�����
		
	}
	private void updateServerPort(String port) {
		prefServerPort.setSummary(port);
//		prefServerPort.setEnabled(checkboxRemoteDebugEnable.isChecked());
	}
	private void updateIp(String port) {
		WifiManager wifiManager = (WifiManager) this.getActivity()
				.getSystemService(Context.WIFI_SERVICE);
		if (!wifiManager.isWifiEnabled()) {
			ip = "δ����WIFI";
		} else {
			ip = intToip(wifiManager.getConnectionInfo().getIpAddress());
		}
		editTextPref.setSummary(ip + ":" + this.adbPort);
	}

	private String intToip(int i) {
		return (i & 0xFF) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff)
				+ "." + ((i >> 24) & 0xff);
	}

	private void alert(final String msg) {
		handler.post(new Runnable() {
			public void run() {
				new AlertDialog.Builder(SettingFragment.this.getActivity()).setTitle(msg)
				.setPositiveButton("ȷ��", null).create().show();
			}
		});
		
	}
	private String toastLong(final String msg) {
		handler.post(new Runnable() {
			public void run() {
				Toast.makeText(SettingFragment.this.getActivity(), msg, Toast.LENGTH_LONG)
						.show();
			}
		});
		return msg;

	}
	
	private void asynStartRemoteDebug() {
		new Thread(){
			public void run(){
				try {
					if(!hasRight){ //û��Ȩ�ޣ����޷�app�Լ��������ߵ��ԣ�����Ҫ���
						try{
							ServerSocket server = new ServerSocket(Integer.parseInt(adbPort));
							server.close();
							//û�б�ռ�ã���Ҫ��ʾ
							Intent intent = new Intent();
							intent.setClass(SettingFragment.this.getActivity(), NetworkAdbActivity.class);
							startActivity(intent);
							
						}catch(Throwable e){//��ռ�þͻ����쳣������˵�Ǻ���,������ʾ�ֹ���������ADB��ֻ��Ҫ�������������ӾͿ���
							sendSettingChangedBoradcast();
						}
					}else{
						sendSettingChangedBoradcast();
					}
					
				} catch (Exception e) {
					alert(e.getMessage());
				}
				
			}
		}.start();
		
	}
	

	private void asynEnableCheckBox(final CheckBoxPreference cb,final String summery) {
		handler.postDelayed(new Runnable(){
			public void run(){
				cb.setSummary(summery);
				cb.setEnabled(true);
			}
		},100);
	}

	private void updateRemoteCheckBox(boolean isServerConnected) {
		checkboxRemoteDebugEnable.setEnabled(true);
		if(isServerConnected&&checkboxRemoteDebugEnable.isChecked()){
			checkboxRemoteDebugEnable.setSummary("�Ѿ����ӵ������������Կ�ʼԶ�̵���");
		}else if(checkboxRemoteDebugEnable.isChecked()){
			checkboxRemoteDebugEnable.setSummary("����������Ӧ�����ڳ������ӷ�����");
		}else{
			checkboxRemoteDebugEnable.setSummary("δ����");
		}
	}
	

}
