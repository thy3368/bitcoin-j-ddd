package com.tanggo.fund.eth.lib.domain.other;

import java.math.BigInteger;
import java.util.Stack;

/**
 * 一个极简的EVM解释器示例（不依赖任何第三方库）
 */
public class SimpleEVM {
    private Stack<BigInteger> stack = new Stack<>();
    private byte[] memory = new byte[1024]; // 简化内存模型
    private long gasRemaining = 1000000; // 初始Gas设置

    // 操作码常量（完整EVM有256个操作码，此处仅定义少数几个）
    private static final byte OP_PUSH1 = 0x60;
    private static final byte OP_ADD = 0x01;
    private static final byte OP_MUL = 0x02;

    /**
     * 执行字节码的核心方法
     * @param bytecode 智能合约编译后的字节码
     * @return 执行成功与否
     */
    public boolean execute(byte[] bytecode) {
        int pc = 0; // 程序计数器，指向当前执行的字节码位置
        while (pc < bytecode.length && gasRemaining > 0) {
            byte opcode = bytecode[pc];
            pc++;

            switch (opcode) {
                case OP_PUSH1:
                    // PUSH1后跟1个字节的数据，将其压入栈
                    if (pc < bytecode.length) {
                        BigInteger value = BigInteger.valueOf(bytecode[pc] & 0xFF); // 转换为无符号整数
                        stack.push(value);
                        pc++;
                        gasRemaining -= 3; // 假设PUSH1消耗3单位Gas
                    }
                    break;

                case OP_ADD:
                    if (stack.size() >= 2) {
                        BigInteger a = stack.pop();
                        BigInteger b = stack.pop();
                        stack.push(a.add(b));
                        gasRemaining -= 3; // 假设ADD消耗3单位Gas
                    } else {
                        System.err.println("Stack underflow on ADD");
                        return false;
                    }
                    break;

                case OP_MUL:
                    if (stack.size() >= 2) {
                        BigInteger a = stack.pop();
                        BigInteger b = stack.pop();
                        stack.push(a.multiply(b));
                        gasRemaining -= 5; // 假设MUL消耗5单位Gas
                    } else {
                        System.err.println("Stack underflow on MUL");
                        return false;
                    }
                    break;

                default:
                    System.err.println("Unsupported opcode: 0x" + Integer.toHexString(opcode & 0xFF));
                    return false;
            }

            // 检查Gas是否耗尽
            if (gasRemaining <= 0) {
                System.err.println("Out of Gas");
                return false;
            }
        }
        System.out.println("Execution completed. Gas remaining: " + gasRemaining);
        return true;
    }

    // 测试示例
    public static void main(String[] args) {
        SimpleEVM evm = new SimpleEVM();
        // 模拟一个简单的字节码序列: PUSH1 2, PUSH1 3, ADD, PUSH1 4, MUL
        // 预期结果: (2 + 3) * 4 = 20
        byte[] bytecode = {
                OP_PUSH1, 2,   // 压入数值2
                OP_PUSH1, 3,   // 压入数值3
                OP_ADD,         // 相加得到5
                OP_PUSH1, 4,   // 压入数值4
                OP_MUL          // 相乘得到20
        };
        evm.execute(bytecode);
        if (!evm.stack.isEmpty()) {
            System.out.println("Result on stack: " + evm.stack.pop());
        }
        System.out.println("Result on stack: ");

    }
}
