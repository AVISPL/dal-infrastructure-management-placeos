package com.vizcom.symphony.dal.communicator.vizcom.place;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.avispl.symphony.api.dal.Device;
import com.avispl.symphony.api.dal.Version;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Button;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.DropDown;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Numeric;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Preset;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Slider;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Switch;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty.Text;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.vizcom.symphony.dal.communicator.vizcom.common.ISymphonyConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import okhttp3.*;
import okhttp3.FormBody.Builder;

import java.io.IOException;

// ACA<->Symphony Aggregator DAL
public class PlaceOS implements ISymphonyConfig, Device, Aggregator, Controller {
	protected final Log logger = LogFactory.getLog(PlaceOS.class);
	/**
	 * Vizcom
	 */
	public final String APPLICATION_JSON = "application/json";
	private final static String VERSION = "1.0.0";

	private final OkHttpClient httpClient = new OkHttpClient();
	private final JSONParser JSON = new JSONParser();

	private Boolean initWasCalled = false;

	private String host;
	private String system;
	private String aca_device_name;
	private int port = -1;
	private String protocol;

	private PlaceAuthToken token;

	public PlaceOS() {
	}

	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("~~~~ GET STATISTICS Version: " + VERSION + " " + PlaceOS.class);
		}
		if (this.token == null || !this.token.isValid()) {
			oauth2PasswordGrant();
		}
		// empty body with application/json
		RequestBody body = RequestBody.create("[]".getBytes(), MediaType.parse(APPLICATION_JSON));
		String endpoint = getSystemURI() + "/devices";

		// add header add body
		Request request = new Request.Builder().url(endpoint)
				.addHeader("Authorization", "Bearer " + this.token.getAccessToken()).post(body).build();

		// make the request
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				if (logger.isErrorEnabled()) {
					logger.error("~~~~ GET STATISTICS Version: " + VERSION + " " + PlaceOS.class);
				}
				throw new Exception("Unexpected code " + response);
			}

			ResponseBody responseBody = response.body();
			String data = responseBody.string();
			// placeOS returns a double jsonEncoded strings to allow for sending empty
			// strings without error
			String stringWrapping = (String) JSON.parse(data);
			JSONArray jsonData = (JSONArray) JSON.parse(stringWrapping);
			return buildAggregatedDevices(jsonData);
		} catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("~~~~ GET STATISTICS Version: " + VERSION + " " + PlaceOS.class);
			}
			throw new Exception(e);
		}
	}

	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("~~~~ DO CONTROLLABLE Version: " + VERSION + " " + PlaceOS.class);
		}
		if (this.token == null || !this.token.isValid()) {
			oauth2PasswordGrant();
		}

		// get the control properties and add to list
		List<Object> controlList = new ArrayList<Object>();
		controlList.add((String) controllableProperty.getDeviceId());
		controlList.add((String) controllableProperty.getProperty());
		controlList.add((Object) controllableProperty.getValue());
		String bodyData = JSONArray.toJSONString(controlList);
		/**
		 * [devId, property, value]
		 */
		RequestBody body = RequestBody.create(bodyData.getBytes(), MediaType.parse(APPLICATION_JSON));
		String endpoint = getSystemURI() + "/control";
		Request request = new Request.Builder().url(endpoint)
				.addHeader("Authorization", "Bearer " + this.token.getAccessToken()).post(body).build();

		// dont really care about the response here
		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onResponse(Call arg0, Response response) throws IOException {
				if (logger.isDebugEnabled()) {
					logger.debug(
							"~~~~ Control POST: " + VERSION + " " + PlaceOS.class + "Response: " + response.code());
				}
			}

			@Override
			public void onFailure(Call arg0, IOException arg1) {
				if (logger.isDebugEnabled()) {
					logger.debug(
							"~~~~ Control POST: " + VERSION + " " + PlaceOS.class + "Reason: " + arg1.getMessage());
				}
			}
		});
		return;
	}

	// filter collected statistics to requested statistics
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> deviceIds) throws Exception {
		return retrieveMultipleStatistics().stream().filter(device -> deviceIds.contains(device.getDeviceId()))
				.collect(Collectors.toList());
	}

	// call for each property
	@Override
	public void controlProperties(List<ControllableProperty> controllableProperties) throws Exception {
		for (ControllableProperty controllableProperty : controllableProperties) {
			controlProperty(controllableProperty);
		}
	}

	@Override
	public Version retrieveSoftwareVersion() throws Exception {
		return new Version(VERSION);
	}

	@Override
	public void destroy() {
		if (logger.isInfoEnabled()) {
			logger.info("~~~~ Destroy Device Version: " + VERSION + " " + PlaceOS.class);
		}
	}

	@Override
	public void init() throws Exception {
		this.initWasCalled = true;
		if (logger.isInfoEnabled()) {
			logger.info("~~~~ Initialise Device Version: " + VERSION + " " + PlaceOS.class);
		}
	}

	// confirm required properties are set
	@Override
	public boolean isInitialized() {
		return (this.initWasCalled && this.host != null && this.port != -1 && this.protocol != null
				&& this.system != null && this.aca_device_name != null);
	}

	/**
	 * <host> Hostname will be in the form vizcom.dev.place.tech
	 */
	@Override
	public void setHost(String host) {
		this.host = host;
	}

	@Override
	public String getAddress() {
		return this.host;
	}

	@Override
	public void setPort(int port) {
		this.port = port;
	}

	@Override
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	/**
	 * Username will be in the form <system>:<module_index>
	 */
	@Override
	public void setLogin(String username) {
		String[] x = username.split(":");
		if (x.length >= 2)
			this.aca_device_name = x[1];
		if (x.length >= 1)
			this.system = x[0];
	}

	@Override
	public void setPassword(String password) {
	}

	protected void setToken(PlaceAuthToken token) {
		this.token = token;
	};

	// e.g: https://vizcom.placeos.tech:8000
	private String getBaseURI() {
		return this.protocol + "://" + this.host + ":" + this.port;
	}

	// e.g: https://vizcom.placeos.tech:8000/api/engine/v2/systems/<system>
	private String getSystemURI() {
		return getBaseURI() + "/api/engine/v2/systems/" + this.system + "/" + this.aca_device_name;
	}

	private void oauth2PasswordGrant() throws Exception {
		String uri = new StringBuilder().append(getBaseURI()).append("/auth/oauth/token").toString();

		// read environment variables
		String username = null;
		String password = null;
		String client_id = null;
		String client_secret = null;
		try {
			username = System.getenv("PLACEOS_USERNAME");
			password = System.getenv("PLACEOS_PASSWORD");
			client_id = System.getenv("PLACEOS_CLIENT_ID");
			client_secret = System.getenv("PLACEOS_CLIENT_SECRET");
		} catch (Exception e) {
			throw new Exception(e);
		}

		if (username == null || password == null || client_id == null || client_secret == null) {
			throw new Exception("Unset PLACEOS Environment Variable");
		}

		Builder formBuilder = new FormBody.Builder();
		formBuilder.add("grant_type", "password");
		formBuilder.add("username", username);
		formBuilder.add("password", password);
		formBuilder.add("client_id", client_id);
		formBuilder.add("client_secret", client_secret);
		Request request = new Request.Builder().url(uri).post(formBuilder.build()).build();

		// get and parse the Placeos Token
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new Exception("Unexpected code " + response);
			}

			ResponseBody responseBody = response.body();
			String data = responseBody.string();
			setToken(new PlaceAuthToken(data));
		} catch (Exception e) {
			throw new Exception(e);
		}
	}

	private List<AggregatedDevice> buildAggregatedDevices(JSONArray data) throws Exception {
		List<AggregatedDevice> ag = new ArrayList<AggregatedDevice>();
		// for each returned device
		for (int i = 0; i < data.size(); i++) {
			JSONObject device = (JSONObject) data.get(i);
			AggregatedDevice ad = new AggregatedDevice();
			ad.setDeviceType((String) device.get("deviceType"));
			ad.setDeviceId((String) device.get("deviceId"));
			ad.setDeviceName((String) device.get("deviceName"));
			ad.setDeviceMake((String) device.get("deviceMake"));
			ad.setDeviceModel((String) device.get("deviceModel"));
			ad.setSerialNumber((String) device.get("serialNumber"));
			ad.setDeviceOnline((Boolean) device.get("deviceOnline"));
			// convert jsonArray to List<String>
			String[] macAddressesArray = toStringArray((JSONArray) device.get("macAddresses"));
			ad.setMacAddresses(Arrays.asList(macAddressesArray));

			// for each statistic add to a map
			JSONObject stats = (JSONObject) device.get("statistics");
			Map<String, String> statistics = new HashMap<String, String>();
			for (Object key : stats.keySet()) {
				statistics.put((String) key, (String) String.valueOf(stats.get(key)));
			}
			ad.setStatistics(statistics);
			ad.setMonitoredStatistics(new ArrayList<Statistics>());

			// for each property add to a map
			JSONObject props = (JSONObject) device.get("properties");
			Map<String, String> properties = new HashMap<String, String>();
			for (Object key : props.keySet()) {
				properties.put((String) key, (String) String.valueOf(props.get(key)));
			}
			ad.setProperties(properties);

			// process advanced controls
			List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<AdvancedControllableProperty>();
			try {
				JSONObject ctrlMap = (JSONObject) device.get("control");
				// ctrlMap contains name => advanced control properties
				for (Object name : ctrlMap.keySet()) {
					JSONObject advancedControl = (JSONObject) JSON.parse((String) ctrlMap.get(name));
					// advancedControl is a map of
					// controltypeL e.g. button, slider
					// to the options
					// for each control type get the options,
					for (Object controlType : advancedControl.keySet()) {
						JSONObject controlOptions = (JSONObject) advancedControl.get(controlType);
						AdvancedControllableProperty acp = buildControl((String) controlType, controlOptions);
						acp.setName((String) name);
						acp.setTimestamp(new Date());
						// not sure why i need a value here
						acp.setValue((String) statistics.get(name));
						advancedControllableProperties.add(acp);
					}
				}
				ad.setControllableProperties(advancedControllableProperties);
			} catch (Exception e) {
				throw new Exception(e);
			}

			// add the aggregated device to the list of aggregated devices
			ag.add(ad);
		}
		return ag;
	}

	private AdvancedControllableProperty buildControl(String type, JSONObject control) {
		AdvancedControllableProperty property = new AdvancedControllableProperty();

		switch (type) {
			case "button":
				AdvancedControllableProperty.Button button = new Button();
				button.setLabel((String) control.get("label"));
				button.setLabelPressed((String) control.get("labelPressed"));
				button.setGracePeriod((Long) control.get("gracePeriod"));
				property.setType(button);
				break;
			case "switch":
				AdvancedControllableProperty.Switch switchx = new Switch();

				switchx.setLabelOn((String) control.get("labelOn"));
				switchx.setLabelOff((String) control.get("labelOff"));
				property.setType(switchx);
				break;
			case "dropdown":
				AdvancedControllableProperty.DropDown dropdown = new DropDown();
				dropdown.setLabels(toStringArray((JSONArray) control.get("labels")));
				dropdown.setOptions(toStringArray((JSONArray) control.get("options")));
				property.setType(dropdown);
				break;
			case "preset":
				AdvancedControllableProperty.Preset preset = new Preset();
				preset.setLabels(toStringArray((JSONArray) control.get("labels")));
				preset.setOptions(toStringArray((JSONArray) control.get("options")));
				property.setType(preset);
				break;
			case "slider":
				AdvancedControllableProperty.Slider slider = new Slider();
				double rangeStart = (double) control.get("rangeStart");
				double rangeEnd = (double) control.get("rangeEnd");
				slider.setLabelStart((String) control.get("labelStart"));
				slider.setLabelEnd((String) control.get("labelEnd"));
				slider.setRangeStart((float) rangeStart);
				slider.setRangeEnd((float) rangeEnd);
				property.setType(slider);
				break;
			case "text":
				AdvancedControllableProperty.Text text = new Text();
				property.setType(text);
				break;
			case "numeric":
				AdvancedControllableProperty.Numeric numeric = new Numeric();
				property.setType(numeric);
				break;
			default:
				break;
		}
		return property;
	}

	private String[] toStringArray(JSONArray array) {
		if (array == null)
			return null;

		String[] arr = new String[array.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = (String) array.get(i);
		}
		return arr;
	}
}
