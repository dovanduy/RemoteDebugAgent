package com.aiitec.debugAgent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 
 * @author dev Aiitec.inc
 * {@link www.aiitec.com}
 *
 */
public class BootBroadcastReceiver extends BroadcastReceiver {
	// ��дonReceive����
	@Override
	public void onReceive(Context context, Intent intent) {
		// ��ߵ�XXX.class����Ҫ�����ķ���
		Intent service = new Intent(context, AgentService.class);
		context.startService(service);
		
		Log.i("TAG", "�����Զ������Զ�����.....");
		
	}

}
