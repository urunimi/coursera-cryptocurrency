package com.hovans.cc.scrooge;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

public class MaxFeeTxHandler {

    private UTXOPool pool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        pool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null) return false;

        double inSum = 0, outSum = 0;

        HashSet<UTXO> usedTxs = new HashSet<UTXO>();

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            if (input == null) return false;
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1), (3)
            if (pool.contains(utxo) == false || usedTxs.contains(utxo)) {
                return false;
            }

            Transaction.Output prevTxOutput = pool.getTxOutput(utxo);

            // (2)
            PublicKey key = prevTxOutput.address;
            if (Crypto.verifySignature(key, tx.getRawDataToSign(i), input.signature) == false) {
                return false;
            }
            // (3)
            usedTxs.add(utxo);

            inSum += prevTxOutput.value;
        }

        // (4)
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            outSum += output.value;
        }

        return inSum >= outSum;
    }

    Double getTransactionFees(Transaction tx) {
        double inSum = 0, outSum = 0;

        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!pool.contains(utxo) || !isValidTx(tx)) continue;
            Transaction.Output txOutput = pool.getTxOutput(utxo);
            inSum += txOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            outSum += output.value;
        }
        return inSum - outSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public synchronized Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        if (possibleTxs == null || possibleTxs.length == 0) {
            return new Transaction[0];
        }
        SortedSet<Transaction> validTxs = new TreeSet<>(((o1, o2) -> getTransactionFees(o2).compareTo(getTransactionFees(o1))));

        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx) == false || validTxs.contains(tx)) {
                continue;
            }
            for (Transaction.Input input : tx.getInputs()) {
                pool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
            }

            for (int i = 0; i < tx.numOutputs(); i++) {
                pool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i));
            }

            validTxs.add(tx);
        }

        ArrayList<Transaction> transactions = new ArrayList<>(validTxs.size());

        for (Transaction tx : validTxs) {
            transactions.add(tx);
            System.out.println(getTransactionFees(tx));
        }

        return transactions.toArray(new Transaction[validTxs.size()]);
    }
}
