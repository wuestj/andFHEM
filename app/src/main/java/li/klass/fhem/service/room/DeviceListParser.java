/*
 * AndFHEM - Open Source Android application to control a FHEM home automation
 * server.
 *
 * Copyright (c) 2011, Matthias Klass or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU GENERAL PUBLIC LICENSE, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU GENERAL PUBLIC LICENSE
 * for more details.
 *
 * You should have received a copy of the GNU GENERAL PUBLIC LICENSE
 * along with this distribution; if not, write to:
 *   Free Software Foundation, Inc.
 *   51 Franklin Street, Fifth Floor
 *   Boston, MA  02110-1301  USA
 */

package li.klass.fhem.service.room;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import li.klass.fhem.AndFHEMApplication;
import li.klass.fhem.R;
import li.klass.fhem.constants.Actions;
import li.klass.fhem.constants.BundleExtraKeys;
import li.klass.fhem.domain.FileLogDevice;
import li.klass.fhem.domain.core.Device;
import li.klass.fhem.domain.core.DeviceType;
import li.klass.fhem.domain.core.RoomDeviceList;
import li.klass.fhem.domain.log.LogDevice;
import li.klass.fhem.error.ErrorHolder;
import li.klass.fhem.fhem.RequestResult;
import li.klass.fhem.fhem.RequestResultError;
import li.klass.fhem.util.StringEscapeUtil;
import li.klass.fhem.util.StringUtil;
import li.klass.fhem.util.XMLUtil;

import static li.klass.fhem.domain.core.DeviceFunctionality.LOG;


/**
 * Class responsible for reading the current xml list from FHEM.
 */
public class DeviceListParser {

    public static final DeviceListParser INSTANCE = new DeviceListParser();
    public static final String TAG = DeviceListParser.class.getName();

    private Map<Class<Device>, Map<String, Set<Method>>> deviceClassCache;

    private class ReadErrorHolder {
        private Map<DeviceType, Integer> deviceTypeErrorCount = new HashMap<DeviceType, Integer>();

        public int getErrorCount() {
            int errors = 0;
            for (Integer deviceTypeErrors : deviceTypeErrorCount.values()) {
                errors += deviceTypeErrors;
            }
            return errors;
        }

        public boolean hasErrors() {
            return deviceTypeErrorCount.size() != 0;
        }

        public void addError(DeviceType deviceType) {
            addErrors(deviceType, 1);
        }

        public void addErrors(DeviceType deviceType, int errorCount) {
            int count = 0;
            if (deviceTypeErrorCount.containsKey(deviceType)) {
                count = deviceTypeErrorCount.get(deviceType);
            }
            deviceTypeErrorCount.put(deviceType, count + errorCount);
        }

        public List<String> getErrorDeviceTypeNames() {
            if (deviceTypeErrorCount.size() == 0) return Collections.emptyList();

            List<String> errorDeviceTypeNames = new ArrayList<String>();
            for (DeviceType deviceType : deviceTypeErrorCount.keySet()) {
                errorDeviceTypeNames.add(deviceType.name());
            }

            return errorDeviceTypeNames;
        }
    }

    private DeviceListParser() {
    }

    public Map<String, RoomDeviceList> parseAndWrapExceptions(String xmlList) {
        try {
            return parseXMLList(xmlList);
        } catch (Exception e) {
            Log.e(TAG, "cannot parse xmllist", e);
            ErrorHolder.setError(e, "cannot parse xmllist.");

            new RequestResult<String>(RequestResultError.DEVICE_LIST_PARSE).handleErrors();
            return null;
        }
    }

    private Map<String, RoomDeviceList> parseXMLList(String xmlList) throws Exception {
        if (xmlList != null) {
            xmlList = xmlList.trim();
        }

        Map<String, RoomDeviceList> roomDeviceListMap = new HashMap<String, RoomDeviceList>();
        RoomDeviceList allDevicesRoom = new RoomDeviceList(RoomDeviceList.ALL_DEVICES_ROOM);

        if (xmlList == null || "".equals(xmlList)) {
            Log.e(TAG, "xmlList is null or blank");
            return roomDeviceListMap;
        }

        // if a newline happens after a set followed by an attrs, both attributes are appended together without
        // adding a whitespace
        xmlList = xmlList.replaceAll("=\"\"attrs", "=\"\" attrs");

        // replace html attribute
        xmlList = xmlList.replaceAll("<ATTR key=\"htmlattr\"[ A-Za-z0-9=\"]*/>", "");

        xmlList = xmlList.replaceAll("</>", "");
        xmlList = xmlList.replaceAll("< [^>]*>", "");

        //replace values with an unset tag
        xmlList = xmlList.replaceAll("< name=[a-zA-Z\"=0-9 ]+>", "");

        xmlList = xmlList.replaceAll("<_internal__LIST>[\\s\\S]*</_internal__LIST>", "");
        xmlList = xmlList.replaceAll("<notify_LIST[\\s\\S]*</notify_LIST>", "");
        xmlList = xmlList.replaceAll("<CUL_IR_LIST>[\\s\\S]*</CUL_IR_LIST>", "");
        xmlList = xmlList.replaceAll("<autocreate_LIST>[\\s\\S]*</autocreate_LIST>", "");
        xmlList = xmlList.replaceAll("<Global_LIST[\\s\\S]*</Global_LIST>", "");

        xmlList = xmlList.replaceAll("_internal_", "internal");

        // fix for invalid umlauts
        xmlList = xmlList.replaceAll("&#[\\s\\S]*;", "");

        // remove "" not being preceded by an =
        xmlList = xmlList.replaceAll("(?:[^=])\"\"+", "\"");
        xmlList = xmlList.replaceAll("\\\\B0", "°");
        xmlList = xmlList.replaceAll("Â", "");

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document document = docBuilder.parse(new InputSource(new StringReader(xmlList)));

        ReadErrorHolder errorHolder = new ReadErrorHolder();

        DeviceType[] deviceTypes = DeviceType.values();
        for (DeviceType deviceType : deviceTypes) {
            int localErrorCount = devicesFromDocument(deviceType.getDeviceClass(), roomDeviceListMap, document, deviceType.getXmllistTag(),
                    allDevicesRoom);
            if (localErrorCount > 0) {
                errorHolder.addErrors(deviceType, localErrorCount);
            }
        }

        addLogsToDevices(allDevicesRoom);
        performAfterReadOperations(allDevicesRoom, roomDeviceListMap, errorHolder);

        if (errorHolder.hasErrors()) {
            Context context = AndFHEMApplication.getContext();
            String errorMessage = context.getString(R.string.errorDeviceListLoad);
            String deviceTypesError = StringUtil.concatenate(errorHolder.getErrorDeviceTypeNames(), ",");
            errorMessage = String.format(errorMessage, "" + errorHolder.getErrorCount(), deviceTypesError);

            Intent intent = new Intent(Actions.SHOW_TOAST);
            intent.putExtra(BundleExtraKeys.CONTENT, errorMessage);
            context.sendBroadcast(intent);
        }

        Log.e(TAG, "loaded " + allDevicesRoom.getAllDevices().size() + " devices!");

        return roomDeviceListMap;
    }

    private void performAfterReadOperations(RoomDeviceList allDevicesRoom,
                                            Map<String, RoomDeviceList> roomDeviceListMap, ReadErrorHolder errorHolder) {
        for (Device device : allDevicesRoom.getAllDevices()) {
            try {
                device.afterXMLRead();
                removeIfUnsupported(device, allDevicesRoom, roomDeviceListMap);
            } catch (Exception e) {
                remove(device, allDevicesRoom, roomDeviceListMap);
                errorHolder.addError(DeviceType.getDeviceTypeFor(device));
                Log.e(TAG, "cannot perform after read operations", e);
            }
        }
    }

    private void removeIfUnsupported(Device device, RoomDeviceList allDevicesRoom, Map<String, RoomDeviceList> roomDeviceListMap) {
        if (device.isSupported()) return;

        remove(device, allDevicesRoom, roomDeviceListMap);
    }

    private void remove(Device device, RoomDeviceList allDevicesRoom, Map<String, RoomDeviceList> roomDeviceListMap) {
        for (String room : device.getRooms()) {
            RoomDeviceList roomDeviceList = roomDeviceListMap.get(room);
            roomDeviceList.removeDevice(device);
        }

        allDevicesRoom.removeDevice(device);
    }

    /**
     * @param deviceClass       class of the device to read
     * @param roomDeviceListMap rooms device list map to read the device into.
     * @param document          xml document to read
     * @param tagName           current tag name to read
     * @param <T>               type of device
     * @return error count while parsing the device list
     */
    private <T extends Device> int devicesFromDocument(Class<T> deviceClass, Map<String,
            RoomDeviceList> roomDeviceListMap, Document document, String tagName, RoomDeviceList allDevicesRoom) {

        int errorCount = 0;

        NodeList nodes = document.getElementsByTagName(tagName);
        String errorXML = "";

        for (int i = 0; i < nodes.getLength(); i++) {
            Node item = nodes.item(i);

            if (!deviceFromNode(deviceClass, roomDeviceListMap, item, allDevicesRoom)) {
                errorCount++;
                errorXML += XMLUtil.nodeToString(item) + "\r\n\r\n";
            }
        }

        if (errorCount > 0) {
            ErrorHolder.setError("Cannot parse devices: \r\n" + errorXML);
        }

        return errorCount;
    }

    /**
     * Instantiates a new device from the given device class. The current {@link Node} to read will be provided to
     * the device, so that it can extract any values.
     *
     * @param deviceClass       class to instantiate
     * @param roomDeviceListMap map used for saving the device
     * @param node              current xml node
     * @param <T>               specific device type
     * @return true if everything went well
     */
    private <T extends Device> boolean deviceFromNode(Class<T> deviceClass, Map<String, RoomDeviceList> roomDeviceListMap,
                                                      Node node, RoomDeviceList allDevicesRoom) {
        try {
            T device = createAndFillDevice(deviceClass, node, allDevicesRoom);
            Log.d(TAG, "loaded device with name " + device.getName());

            String[] rooms = device.getRooms();
            for (String room : rooms) {
                RoomDeviceList roomDeviceList = getOrCreateRoomDeviceList(room, roomDeviceListMap);
                roomDeviceList.addDevice(device);
            }
            allDevicesRoom.addDevice(device);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "error parsing device", e);
            return false;
        }
    }

    /**
     * Returns the {@link RoomDeviceList} if it is already included within the room-device list map. Otherwise,
     * the appropriate list will be created, put into the map and returned.
     *
     * @param roomName          room name
     * @param roomDeviceListMap current map including room names and associated device lists.
     * @return matching {@link RoomDeviceList}
     */
    private RoomDeviceList getOrCreateRoomDeviceList(String roomName, Map<String, RoomDeviceList> roomDeviceListMap) {
        if (roomDeviceListMap.containsKey(roomName)) {
            return roomDeviceListMap.get(roomName);
        }
        RoomDeviceList roomDeviceList = new RoomDeviceList(roomName);
        roomDeviceListMap.put(roomName, roomDeviceList);
        return roomDeviceList;
    }

    /**
     * Walks through all {@link li.klass.fhem.domain.FileLogDevice}s and tries to find the matching {@link Device} it
     * is associated to.
     */
    private void addLogsToDevices(RoomDeviceList allDevicesRoom) {
        Collection<Device> devices = allDevicesRoom.getAllDevices();

        Collection<LogDevice> logDevices = allDevicesRoom.getDevicesOfFunctionality(LOG, false);
        for (LogDevice logDevice : logDevices) {
            addLogToDevices(logDevice, devices);
        }
    }

    /**
     * Walks through all devices and tries to find the matching {@link Device} for one given {@link FileLogDevice}.
     *
     * @param fileLogDevice {@link FileLogDevice}, of which the matching {@link Device} is searched
     * @param devices       devices to walk through.
     */
    private void addLogToDevices(LogDevice fileLogDevice, Collection<Device> devices) {
        for (Device device : devices) {
            if (fileLogDevice.concernsDevice(device.getName())) {
                device.setLogDevice(fileLogDevice);
                return;
            }
        }
    }

    private <T extends Device> T createAndFillDevice(Class<T> deviceClass, Node node, RoomDeviceList allDevicesRoom) throws Exception {
        T device = deviceClass.newInstance();
        Map<String, Set<Method>> cache = getDeviceClassCacheEntriesFor(deviceClass);

        NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            String name = item.getNodeName().toUpperCase().replaceAll("[-.]", "_");
            String value = StringEscapeUtil.unescape(item.getNodeValue());

            device.onAttributeRead(name, value);
        }

        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item == null || item.getAttributes() == null) continue;

            Node keyAttribute = item.getAttributes().getNamedItem("key");
            if (keyAttribute == null) continue;

            String originalKey = keyAttribute.getNodeValue().trim().replaceAll("[-\\.]", "_");
            if (! device.acceptXmlKey(originalKey)) {
                continue;
            }

            String keyValue = originalKey.toUpperCase();
            String nodeContent = StringEscapeUtil.unescape(item.getAttributes().getNamedItem("value").getNodeValue());

            if (nodeContent == null || nodeContent.length() == 0) {
                continue;
            }

            if (keyValue.equalsIgnoreCase("device")) {
                device.setAssociatedDeviceCallback(new AssociatedDeviceCallback(nodeContent, allDevicesRoom));
            }

            invokeDeviceAttributeMethod(cache, device, keyValue, nodeContent, item.getAttributes(), item.getNodeName());
        }

        return device;
    }

    private <T extends Device> void invokeDeviceAttributeMethod(Map<String, Set<Method>> cache, T device, String key,
                                                                String value, NamedNodeMap attributes, String tagName) throws Exception {

        device.onChildItemRead(tagName, key, value, attributes);
        if (!cache.containsKey(key)) return;
        Set<Method> availableMethods = cache.get(key);
        for (Method availableMethod : availableMethods) {
            Class<?>[] parameterTypes = availableMethod.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].equals(String.class)) {
                availableMethod.invoke(device, value);
            }

            if (attributes != null && parameterTypes.length == 2 && parameterTypes[0].equals(String.class) &&
                    parameterTypes[1].equals(NamedNodeMap.class)) {
                availableMethod.invoke(device, value, attributes);
            }

            if (tagName != null && attributes != null && parameterTypes.length == 3 &&
                    parameterTypes[0].equals(String.class) && parameterTypes[1].equals(NamedNodeMap.class) &&
                    parameterTypes[2].equals(String.class)) {
                availableMethod.invoke(device, tagName, attributes, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Device> Map<String, Set<Method>> getDeviceClassCacheEntriesFor(Class<T> deviceClass) {
        Class<Device> clazz = (Class<Device>) deviceClass;
        Map<Class<Device>, Map<String, Set<Method>>> cache = getDeviceClassCache();
        if (!cache.containsKey(clazz)) {
            cache.put(clazz, initDeviceClassCacheEntries(deviceClass));
        }

        return cache.get(clazz);
    }

    /**
     * Loads an initial map of method names (that are parsed to attribute names) incl. the methods polymorphic
     * method parameters.
     *
     * @param deviceClass class of the device
     * @return map of device methods
     */
    private <T extends Device> Map<String, Set<Method>> initDeviceClassCacheEntries(Class<T> deviceClass) {
        Map<String, Set<Method>> cache = new HashMap<String, Set<Method>>();
        Method[] methods = deviceClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (!methodName.startsWith("read")) continue;

            String attributeName = methodName.substring("read".length());
            if (!cache.containsKey(attributeName)) {
                cache.put(attributeName, new HashSet<Method>());
            }
            cache.get(attributeName).add(method);
            method.setAccessible(true);
        }

        return cache;
    }

    private Map<Class<Device>, Map<String, Set<Method>>> getDeviceClassCache() {
        if (deviceClassCache == null) {
            deviceClassCache = new HashMap<Class<Device>, Map<String, Set<Method>>>();
        }

        return deviceClassCache;
    }

    public void fillDeviceWith(Device device, Map<String, String> updates) {
        Class<? extends Device> deviceClass = device.getClass();

        fillDeviceWith(device, updates, deviceClass);
        device.afterXMLRead();
    }

    private boolean fillDeviceWith(Device device, Map<String, String> updates, Class<?> deviceClass) {
        Method[] methods = deviceClass.getDeclaredMethods();

        boolean changed = false;

        for (Method method : methods) {
            if (method.getParameterTypes().length != 1) continue;

            String name = method.getName();
            if (!name.startsWith("read") && !name.startsWith("gcm")) continue;

            name = name.replaceAll("read", "").replaceAll("gcm", "").toUpperCase();
            if (updates.containsKey(name)) {
                try {
                    Log.i(TAG, "invoke " + method.getName());
                    method.setAccessible(true);
                    method.invoke(device, updates.get(name));

                    changed = true;
                } catch (Exception e) {
                    Log.e(TAG, "cannot invoke " + method.getName() + " for argument " + updates.get(name));
                }
            }
        }

        if (deviceClass.getSuperclass() != null) {
            return changed | fillDeviceWith(device, updates, deviceClass.getSuperclass());
        } else {
            return changed;
        }
    }

}
