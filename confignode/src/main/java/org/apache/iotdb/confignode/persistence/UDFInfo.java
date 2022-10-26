/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.confignode.persistence;

import org.apache.iotdb.common.rpc.thrift.TSStatus;
import org.apache.iotdb.commons.snapshot.SnapshotProcessor;
import org.apache.iotdb.commons.udf.UDFInformation;
import org.apache.iotdb.commons.udf.UDFTable;
import org.apache.iotdb.commons.udf.service.UDFExecutableManager;
import org.apache.iotdb.confignode.conf.ConfigNodeConfig;
import org.apache.iotdb.confignode.conf.ConfigNodeDescriptor;
import org.apache.iotdb.confignode.consensus.request.write.function.CreateFunctionPlan;
import org.apache.iotdb.confignode.consensus.request.write.function.DropFunctionPlan;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.udf.api.exception.UDFManagementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class UDFInfo implements SnapshotProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(UDFInfo.class);

  private static final ConfigNodeConfig CONFIG_NODE_CONF =
      ConfigNodeDescriptor.getInstance().getConf();

  private final UDFTable udfTable;
  private final Map<String, String> existedJarToMD5;

  private final UDFExecutableManager udfExecutableManager;

  private final ReentrantLock udfTableLock = new ReentrantLock();

  private final String snapshotFileName = "udf_info.bin";

  public UDFInfo() throws IOException {
    udfTable = new UDFTable();
    existedJarToMD5 = new HashMap<>();
    udfExecutableManager =
        UDFExecutableManager.setupAndGetInstance(
            CONFIG_NODE_CONF.getTemporaryLibDir(), CONFIG_NODE_CONF.getUdfLibDir());
  }

  public void acquireUDFTableLock() {
    LOGGER.info("acquire UDFTableLock");
    udfTableLock.lock();
  }

  public void releaseUDFTableLock() {
    LOGGER.info("release UDFTableLock");
    udfTableLock.unlock();
  }

  /** Validate whether the UDF can be created */
  public void validate(String UDFName, String jarName, String jarMD5) {
    if (udfTable.containsUDF(UDFName)) {
      throw new UDFManagementException(
          String.format("Failed to create UDF [%s], the same name UDF has been created", UDFName));
    }

    if (existedJarToMD5.containsKey(jarName) && !existedJarToMD5.get(jarName).equals(jarMD5)) {
      throw new UDFManagementException(
          String.format(
              "Failed to create UDF [%s], the same name Jar [%s] but different MD5 [%s] has existed",
              UDFName, jarName, jarMD5));
    }
  }

  public boolean needToSaveJar(String jarName) {
    return !existedJarToMD5.containsKey(jarName);
  }

  public TSStatus addUDFInTable(CreateFunctionPlan physicalPlan) {
    try {
      final UDFInformation udfInformation = physicalPlan.getUdfInformation();
      udfTable.addUDFInformation(udfInformation.getFunctionName(), udfInformation);
      existedJarToMD5.put(udfInformation.getJarName(), udfInformation.getJarMD5());
      if (physicalPlan.getJarFile() != null) {
        udfExecutableManager.writeToLibDir(
            ByteBuffer.wrap(physicalPlan.getJarFile().getValues()), udfInformation.getJarName());
      }
      return new TSStatus(TSStatusCode.SUCCESS_STATUS.getStatusCode());
    } catch (Exception e) {
      final String errorMessage =
          String.format(
              "Failed to add UDF [%s] in UDF_Table on Config Nodes, because of %s",
              physicalPlan.getUdfInformation().getFunctionName(), e);
      LOGGER.warn(errorMessage, e);
      return new TSStatus(TSStatusCode.EXECUTE_STATEMENT_ERROR.getStatusCode())
          .setMessage(errorMessage);
    }
  }

  public TSStatus dropFunction(DropFunctionPlan req) {
    // TODO
    return null;
  }

  @Override
  public boolean processTakeSnapshot(File snapshotDir) throws IOException {
    // todo: implementation
    return true;
  }

  @Override
  public void processLoadSnapshot(File snapshotDir) throws IOException {
    // todo: implementation
  }
}
