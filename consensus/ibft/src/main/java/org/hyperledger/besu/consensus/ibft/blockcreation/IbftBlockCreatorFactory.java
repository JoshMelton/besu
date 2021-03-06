/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.ibft.blockcreation;

import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.ValidatorVote;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.Vote;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

public class IbftBlockCreatorFactory {

  private final Function<Long, Long> gasLimitCalculator;
  private final PendingTransactions pendingTransactions;
  protected final ProtocolContext protocolContext;
  protected final ProtocolSchedule protocolSchedule;
  private final Address localAddress;
  final Address miningBeneficiary;

  private volatile Bytes vanityData;
  private volatile Wei minTransactionGasPrice;
  private volatile Double minBlockOccupancyRatio;

  public IbftBlockCreatorFactory(
      final Function<Long, Long> gasLimitCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningParameters miningParams,
      final Address localAddress,
      final Address miningBeneficiary) {
    this.gasLimitCalculator = gasLimitCalculator;
    this.pendingTransactions = pendingTransactions;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.localAddress = localAddress;
    this.minTransactionGasPrice = miningParams.getMinTransactionGasPrice();
    this.minBlockOccupancyRatio = miningParams.getMinBlockOccupancyRatio();
    this.vanityData = miningParams.getExtraData();
    this.miningBeneficiary = miningBeneficiary;
  }

  public IbftBlockCreator create(final BlockHeader parentHeader, final int round) {
    return new IbftBlockCreator(
        localAddress,
        ph -> createExtraData(round, ph),
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        gasLimitCalculator,
        minTransactionGasPrice,
        minBlockOccupancyRatio,
        parentHeader,
        miningBeneficiary);
  }

  public void setExtraData(final Bytes extraData) {
    this.vanityData = extraData.copy();
  }

  public void setMinTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public Bytes createExtraData(final int round, final BlockHeader parentHeader) {
    final VoteTally voteTally =
        protocolContext
            .getConsensusState(IbftContext.class)
            .getVoteTallyCache()
            .getVoteTallyAfterBlock(parentHeader);

    final Optional<ValidatorVote> proposal =
        protocolContext
            .getConsensusState(IbftContext.class)
            .getVoteProposer()
            .getVote(localAddress, voteTally);

    final List<Address> validators = new ArrayList<>(voteTally.getValidators());

    final IbftExtraData extraData =
        new IbftExtraData(
            ConsensusHelpers.zeroLeftPad(vanityData, IbftExtraData.EXTRA_VANITY_LENGTH),
            Collections.emptyList(),
            toVote(proposal),
            round,
            validators);

    return extraData.encode();
  }

  public Address getLocalAddress() {
    return localAddress;
  }

  private static Optional<Vote> toVote(final Optional<ValidatorVote> input) {
    return input
        .map(v -> Optional.of(new Vote(v.getRecipient(), v.getVotePolarity())))
        .orElse(Optional.empty());
  }
}
