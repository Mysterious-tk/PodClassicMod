package com.example.podclassic.util;

import android.text.TextUtils;

import java.text.Collator;
import java.util.HashMap;
import java.util.Locale;

public class PinyinUtil {
    private static class HanziToPinyin {
        private static final char[] UNIHANS = {
                '\u963f', '\u54ce', '\u5b89', '\u80ae', '\u51f9', '\u516b',
                '\u6300', '\u6273', '\u90a6', '\u52f9', '\u9642', '\u5954',
                '\u4f3b', '\u5c44', '\u8fb9', '\u706c', '\u618b', '\u6c43',
                '\u51ab', '\u7676', '\u5cec', '\u5693', '\u5072', '\u53c2',
                '\u4ed3', '\u64a1', '\u518a', '\u5d7e', '\u66fd', '\u66fe',
                '\u5c64', '\u53c9', '\u8286', '\u8fbf', '\u4f25', '\u6284',
                '\u8f66', '\u62bb', '\u6c88', '\u6c89', '\u9637', '\u5403',
                '\u5145', '\u62bd', '\u51fa', '\u6b3b', '\u63e3', '\u5ddb',
                '\u5205', '\u5439', '\u65fe', '\u9034', '\u5472', '\u5306',
                '\u51d1', '\u7c97', '\u6c46', '\u5d14', '\u90a8', '\u6413',
                '\u5491', '\u5446', '\u4e39', '\u5f53', '\u5200', '\u561a',
                '\u6265', '\u706f', '\u6c10', '\u55f2', '\u7538', '\u5201',
                '\u7239', '\u4e01', '\u4e1f', '\u4e1c', '\u543a', '\u53be',
                '\u8011', '\u8968', '\u5428', '\u591a', '\u59b8', '\u8bf6',
                '\u5940', '\u97a5', '\u513f', '\u53d1', '\u5e06', '\u531a',
                '\u98de', '\u5206', '\u4e30', '\u8985', '\u4ecf', '\u7d11',
                '\u4f15', '\u65ee', '\u4f85', '\u7518', '\u5188', '\u768b',
                '\u6208', '\u7ed9', '\u6839', '\u522f', '\u5de5', '\u52fe',
                '\u4f30', '\u74dc', '\u4e56', '\u5173', '\u5149', '\u5f52',
                '\u4e28', '\u5459', '\u54c8', '\u548d', '\u4f44', '\u592f',
                '\u8320', '\u8bc3', '\u9ed2', '\u62eb', '\u4ea8', '\u5677',
                '\u53ff', '\u9f41', '\u4e6f', '\u82b1', '\u6000', '\u72bf',
                '\u5ddf', '\u7070', '\u660f', '\u5419', '\u4e0c', '\u52a0',
                '\u620b', '\u6c5f', '\u827d', '\u9636', '\u5dfe', '\u5755',
                '\u5182', '\u4e29', '\u51e5', '\u59e2', '\u5658', '\u519b',
                '\u5494', '\u5f00', '\u520a', '\u5ffc', '\u5c3b', '\u533c',
                '\u808e', '\u52a5', '\u7a7a', '\u62a0', '\u625d', '\u5938',
                '\u84af', '\u5bbd', '\u5321', '\u4e8f', '\u5764', '\u6269',
                '\u5783', '\u6765', '\u5170', '\u5577', '\u635e', '\u808b',
                '\u52d2', '\u5d1a', '\u5215', '\u4fe9', '\u5941', '\u826f',
                '\u64a9', '\u5217', '\u62ce', '\u5222', '\u6e9c', '\u56d6',
                '\u9f99', '\u779c', '\u565c', '\u5a08', '\u7567', '\u62a1',
                '\u7f57', '\u5463', '\u5988', '\u57cb', '\u5ada', '\u7264',
                '\u732b', '\u4e48', '\u5445', '\u95e8', '\u753f', '\u54aa',
                '\u5b80', '\u55b5', '\u4e5c', '\u6c11', '\u540d', '\u8c2c',
                '\u6478', '\u54de', '\u6bea', '\u55ef', '\u62cf', '\u8149',
                '\u56e1', '\u56d4', '\u5b6c', '\u7592', '\u5a1e', '\u6041',
                '\u80fd', '\u59ae', '\u62c8', '\u5b22', '\u9e1f', '\u634f',
                '\u56dc', '\u5b81', '\u599e', '\u519c', '\u7fba', '\u5974',
                '\u597b', '\u759f', '\u9ec1', '\u90cd', '\u5594', '\u8bb4',
                '\u5991', '\u62cd', '\u7705', '\u4e53', '\u629b', '\u5478',
                '\u55b7', '\u5309', '\u4e15', '\u56e8', '\u527d', '\u6c15',
                '\u59d8', '\u4e52', '\u948b', '\u5256', '\u4ec6', '\u4e03',
                '\u6390', '\u5343', '\u545b', '\u6084', '\u767f', '\u4eb2',
                '\u72c5', '\u828e', '\u4e18', '\u533a', '\u5cd1', '\u7f3a',
                '\u590b', '\u5465', '\u7a63', '\u5a06', '\u60f9', '\u4eba',
                '\u6254', '\u65e5', '\u8338', '\u53b9', '\u909a', '\u633c',
                '\u5827', '\u5a51', '\u77a4', '\u637c', '\u4ee8', '\u6be2',
                '\u4e09', '\u6852', '\u63bb', '\u95aa', '\u68ee', '\u50e7',
                '\u6740', '\u7b5b', '\u5c71', '\u4f24', '\u5f30', '\u5962',
                '\u7533', '\u8398', '\u6552', '\u5347', '\u5c38', '\u53ce',
                '\u4e66', '\u5237', '\u8870', '\u95e9', '\u53cc', '\u8c01',
                '\u542e', '\u8bf4', '\u53b6', '\u5fea', '\u635c', '\u82cf',
                '\u72fb', '\u590a', '\u5b59', '\u5506', '\u4ed6', '\u56fc',
                '\u574d', '\u6c64', '\u5932', '\u5fd1', '\u71a5', '\u5254',
                '\u5929', '\u65eb', '\u5e16', '\u5385', '\u56f2', '\u5077',
                '\u51f8', '\u6e4d', '\u63a8', '\u541e', '\u4e47', '\u7a75',
                '\u6b6a', '\u5f2f', '\u5c23', '\u5371', '\u6637', '\u7fc1',
                '\u631d', '\u4e4c', '\u5915', '\u8672', '\u4eda', '\u4e61',
                '\u7071', '\u4e9b', '\u5fc3', '\u661f', '\u51f6', '\u4f11',
                '\u5401', '\u5405', '\u524a', '\u5743', '\u4e2b', '\u6079',
                '\u592e', '\u5e7a', '\u503b', '\u4e00', '\u56d9', '\u5e94',
                '\u54df', '\u4f63', '\u4f18', '\u625c', '\u56e6', '\u66f0',
                '\u6655', '\u7b60', '\u7b7c', '\u5e00', '\u707d', '\u5142',
                '\u5328', '\u50ae', '\u5219', '\u8d3c', '\u600e', '\u5897',
                '\u624e', '\u635a', '\u6cbe', '\u5f20', '\u957f', '\u9577',
                '\u4f4b', '\u8707', '\u8d1e', '\u4e89', '\u4e4b', '\u5cd9',
                '\u5ea2', '\u4e2d', '\u5dde', '\u6731', '\u6293', '\u62fd',
                '\u4e13', '\u5986', '\u96b9', '\u5b92', '\u5353', '\u4e72',
                '\u5b97', '\u90b9', '\u79df', '\u94bb', '\u539c', '\u5c0a',
                '\u6628', '\u5159', '\u9fc3', '\u9fc4',};

        private static final byte[][] PINYINS = {
                {65, 0, 0, 0, 0, 0}, {65, 73, 0, 0, 0, 0},
                {65, 78, 0, 0, 0, 0}, {65, 78, 71, 0, 0, 0},
                {65, 79, 0, 0, 0, 0}, {66, 65, 0, 0, 0, 0},
                {66, 65, 73, 0, 0, 0}, {66, 65, 78, 0, 0, 0},
                {66, 65, 78, 71, 0, 0}, {66, 65, 79, 0, 0, 0},
                {66, 69, 73, 0, 0, 0}, {66, 69, 78, 0, 0, 0},
                {66, 69, 78, 71, 0, 0}, {66, 73, 0, 0, 0, 0},
                {66, 73, 65, 78, 0, 0}, {66, 73, 65, 79, 0, 0},
                {66, 73, 69, 0, 0, 0}, {66, 73, 78, 0, 0, 0},
                {66, 73, 78, 71, 0, 0}, {66, 79, 0, 0, 0, 0},
                {66, 85, 0, 0, 0, 0}, {67, 65, 0, 0, 0, 0},
                {67, 65, 73, 0, 0, 0}, {67, 65, 78, 0, 0, 0},
                {67, 65, 78, 71, 0, 0}, {67, 65, 79, 0, 0, 0},
                {67, 69, 0, 0, 0, 0}, {67, 69, 78, 0, 0, 0},
                {67, 69, 78, 71, 0, 0}, {90, 69, 78, 71, 0, 0},
                {67, 69, 78, 71, 0, 0}, {67, 72, 65, 0, 0, 0},
                {67, 72, 65, 73, 0, 0}, {67, 72, 65, 78, 0, 0},
                {67, 72, 65, 78, 71, 0}, {67, 72, 65, 79, 0, 0},
                {67, 72, 69, 0, 0, 0}, {67, 72, 69, 78, 0, 0},
                {83, 72, 69, 78, 0, 0}, {67, 72, 69, 78, 0, 0},
                {67, 72, 69, 78, 71, 0}, {67, 72, 73, 0, 0, 0},
                {67, 72, 79, 78, 71, 0}, {67, 72, 79, 85, 0, 0},
                {67, 72, 85, 0, 0, 0}, {67, 72, 85, 65, 0, 0},
                {67, 72, 85, 65, 73, 0}, {67, 72, 85, 65, 78, 0},
                {67, 72, 85, 65, 78, 71}, {67, 72, 85, 73, 0, 0},
                {67, 72, 85, 78, 0, 0}, {67, 72, 85, 79, 0, 0},
                {67, 73, 0, 0, 0, 0}, {67, 79, 78, 71, 0, 0},
                {67, 79, 85, 0, 0, 0}, {67, 85, 0, 0, 0, 0},
                {67, 85, 65, 78, 0, 0}, {67, 85, 73, 0, 0, 0},
                {67, 85, 78, 0, 0, 0}, {67, 85, 79, 0, 0, 0},
                {68, 65, 0, 0, 0, 0}, {68, 65, 73, 0, 0, 0},
                {68, 65, 78, 0, 0, 0}, {68, 65, 78, 71, 0, 0},
                {68, 65, 79, 0, 0, 0}, {68, 69, 0, 0, 0, 0},
                {68, 69, 78, 0, 0, 0}, {68, 69, 78, 71, 0, 0},
                {68, 73, 0, 0, 0, 0}, {68, 73, 65, 0, 0, 0},
                {68, 73, 65, 78, 0, 0}, {68, 73, 65, 79, 0, 0},
                {68, 73, 69, 0, 0, 0}, {68, 73, 78, 71, 0, 0},
                {68, 73, 85, 0, 0, 0}, {68, 79, 78, 71, 0, 0},
                {68, 79, 85, 0, 0, 0}, {68, 85, 0, 0, 0, 0},
                {68, 85, 65, 78, 0, 0}, {68, 85, 73, 0, 0, 0},
                {68, 85, 78, 0, 0, 0}, {68, 85, 79, 0, 0, 0},
                {69, 0, 0, 0, 0, 0}, {69, 73, 0, 0, 0, 0},
                {69, 78, 0, 0, 0, 0}, {69, 78, 71, 0, 0, 0},
                {69, 82, 0, 0, 0, 0}, {70, 65, 0, 0, 0, 0},
                {70, 65, 78, 0, 0, 0}, {70, 65, 78, 71, 0, 0},
                {70, 69, 73, 0, 0, 0}, {70, 69, 78, 0, 0, 0},
                {70, 69, 78, 71, 0, 0}, {70, 73, 65, 79, 0, 0},
                {70, 79, 0, 0, 0, 0}, {70, 79, 85, 0, 0, 0},
                {70, 85, 0, 0, 0, 0}, {71, 65, 0, 0, 0, 0},
                {71, 65, 73, 0, 0, 0}, {71, 65, 78, 0, 0, 0},
                {71, 65, 78, 71, 0, 0}, {71, 65, 79, 0, 0, 0},
                {71, 69, 0, 0, 0, 0}, {71, 69, 73, 0, 0, 0},
                {71, 69, 78, 0, 0, 0}, {71, 69, 78, 71, 0, 0},
                {71, 79, 78, 71, 0, 0}, {71, 79, 85, 0, 0, 0},
                {71, 85, 0, 0, 0, 0}, {71, 85, 65, 0, 0, 0},
                {71, 85, 65, 73, 0, 0}, {71, 85, 65, 78, 0, 0},
                {71, 85, 65, 78, 71, 0}, {71, 85, 73, 0, 0, 0},
                {71, 85, 78, 0, 0, 0}, {71, 85, 79, 0, 0, 0},
                {72, 65, 0, 0, 0, 0}, {72, 65, 73, 0, 0, 0},
                {72, 65, 78, 0, 0, 0}, {72, 65, 78, 71, 0, 0},
                {72, 65, 79, 0, 0, 0}, {72, 69, 0, 0, 0, 0},
                {72, 69, 73, 0, 0, 0}, {72, 69, 78, 0, 0, 0},
                {72, 69, 78, 71, 0, 0}, {72, 77, 0, 0, 0, 0},
                {72, 79, 78, 71, 0, 0}, {72, 79, 85, 0, 0, 0},
                {72, 85, 0, 0, 0, 0}, {72, 85, 65, 0, 0, 0},
                {72, 85, 65, 73, 0, 0}, {72, 85, 65, 78, 0, 0},
                {72, 85, 65, 78, 71, 0}, {72, 85, 73, 0, 0, 0},
                {72, 85, 78, 0, 0, 0}, {72, 85, 79, 0, 0, 0},
                {74, 73, 0, 0, 0, 0}, {74, 73, 65, 0, 0, 0},
                {74, 73, 65, 78, 0, 0}, {74, 73, 65, 78, 71, 0},
                {74, 73, 65, 79, 0, 0}, {74, 73, 69, 0, 0, 0},
                {74, 73, 78, 0, 0, 0}, {74, 73, 78, 71, 0, 0},
                {74, 73, 79, 78, 71, 0}, {74, 73, 85, 0, 0, 0},
                {74, 85, 0, 0, 0, 0}, {74, 85, 65, 78, 0, 0},
                {74, 85, 69, 0, 0, 0}, {74, 85, 78, 0, 0, 0},
                {75, 65, 0, 0, 0, 0}, {75, 65, 73, 0, 0, 0},
                {75, 65, 78, 0, 0, 0}, {75, 65, 78, 71, 0, 0},
                {75, 65, 79, 0, 0, 0}, {75, 69, 0, 0, 0, 0},
                {75, 69, 78, 0, 0, 0}, {75, 69, 78, 71, 0, 0},
                {75, 79, 78, 71, 0, 0}, {75, 79, 85, 0, 0, 0},
                {75, 85, 0, 0, 0, 0}, {75, 85, 65, 0, 0, 0},
                {75, 85, 65, 73, 0, 0}, {75, 85, 65, 78, 0, 0},
                {75, 85, 65, 78, 71, 0}, {75, 85, 73, 0, 0, 0},
                {75, 85, 78, 0, 0, 0}, {75, 85, 79, 0, 0, 0},
                {76, 65, 0, 0, 0, 0}, {76, 65, 73, 0, 0, 0},
                {76, 65, 78, 0, 0, 0}, {76, 65, 78, 71, 0, 0},
                {76, 65, 79, 0, 0, 0}, {76, 69, 0, 0, 0, 0},
                {76, 69, 73, 0, 0, 0}, {76, 69, 78, 71, 0, 0},
                {76, 73, 0, 0, 0, 0}, {76, 73, 65, 0, 0, 0},
                {76, 73, 65, 78, 0, 0}, {76, 73, 65, 78, 71, 0},
                {76, 73, 65, 79, 0, 0}, {76, 73, 69, 0, 0, 0},
                {76, 73, 78, 0, 0, 0}, {76, 73, 78, 71, 0, 0},
                {76, 73, 85, 0, 0, 0}, {76, 79, 0, 0, 0, 0},
                {76, 79, 78, 71, 0, 0}, {76, 79, 85, 0, 0, 0},
                {76, 85, 0, 0, 0, 0}, {76, 85, 65, 78, 0, 0},
                {76, 85, 69, 0, 0, 0}, {76, 85, 78, 0, 0, 0},
                {76, 85, 79, 0, 0, 0}, {77, 0, 0, 0, 0, 0},
                {77, 65, 0, 0, 0, 0}, {77, 65, 73, 0, 0, 0},
                {77, 65, 78, 0, 0, 0}, {77, 65, 78, 71, 0, 0},
                {77, 65, 79, 0, 0, 0}, {77, 69, 0, 0, 0, 0},
                {77, 69, 73, 0, 0, 0}, {77, 69, 78, 0, 0, 0},
                {77, 69, 78, 71, 0, 0}, {77, 73, 0, 0, 0, 0},
                {77, 73, 65, 78, 0, 0}, {77, 73, 65, 79, 0, 0},
                {77, 73, 69, 0, 0, 0}, {77, 73, 78, 0, 0, 0},
                {77, 73, 78, 71, 0, 0}, {77, 73, 85, 0, 0, 0},
                {77, 79, 0, 0, 0, 0}, {77, 79, 85, 0, 0, 0},
                {77, 85, 0, 0, 0, 0}, {78, 0, 0, 0, 0, 0},
                {78, 65, 0, 0, 0, 0}, {78, 65, 73, 0, 0, 0},
                {78, 65, 78, 0, 0, 0}, {78, 65, 78, 71, 0, 0},
                {78, 65, 79, 0, 0, 0}, {78, 69, 0, 0, 0, 0},
                {78, 69, 73, 0, 0, 0}, {78, 69, 78, 0, 0, 0},
                {78, 69, 78, 71, 0, 0}, {78, 73, 0, 0, 0, 0},
                {78, 73, 65, 78, 0, 0}, {78, 73, 65, 78, 71, 0},
                {78, 73, 65, 79, 0, 0}, {78, 73, 69, 0, 0, 0},
                {78, 73, 78, 0, 0, 0}, {78, 73, 78, 71, 0, 0},
                {78, 73, 85, 0, 0, 0}, {78, 79, 78, 71, 0, 0},
                {78, 79, 85, 0, 0, 0}, {78, 85, 0, 0, 0, 0},
                {78, 85, 65, 78, 0, 0}, {78, 85, 69, 0, 0, 0},
                {78, 85, 78, 0, 0, 0}, {78, 85, 79, 0, 0, 0},
                {79, 0, 0, 0, 0, 0}, {79, 85, 0, 0, 0, 0},
                {80, 65, 0, 0, 0, 0}, {80, 65, 73, 0, 0, 0},
                {80, 65, 78, 0, 0, 0}, {80, 65, 78, 71, 0, 0},
                {80, 65, 79, 0, 0, 0}, {80, 69, 73, 0, 0, 0},
                {80, 69, 78, 0, 0, 0}, {80, 69, 78, 71, 0, 0},
                {80, 73, 0, 0, 0, 0}, {80, 73, 65, 78, 0, 0},
                {80, 73, 65, 79, 0, 0}, {80, 73, 69, 0, 0, 0},
                {80, 73, 78, 0, 0, 0}, {80, 73, 78, 71, 0, 0},
                {80, 79, 0, 0, 0, 0}, {80, 79, 85, 0, 0, 0},
                {80, 85, 0, 0, 0, 0}, {81, 73, 0, 0, 0, 0},
                {81, 73, 65, 0, 0, 0}, {81, 73, 65, 78, 0, 0},
                {81, 73, 65, 78, 71, 0}, {81, 73, 65, 79, 0, 0},
                {81, 73, 69, 0, 0, 0}, {81, 73, 78, 0, 0, 0},
                {81, 73, 78, 71, 0, 0}, {81, 73, 79, 78, 71, 0},
                {81, 73, 85, 0, 0, 0}, {81, 85, 0, 0, 0, 0},
                {81, 85, 65, 78, 0, 0}, {81, 85, 69, 0, 0, 0},
                {81, 85, 78, 0, 0, 0}, {82, 65, 78, 0, 0, 0},
                {82, 65, 78, 71, 0, 0}, {82, 65, 79, 0, 0, 0},
                {82, 69, 0, 0, 0, 0}, {82, 69, 78, 0, 0, 0},
                {82, 69, 78, 71, 0, 0}, {82, 73, 0, 0, 0, 0},
                {82, 79, 78, 71, 0, 0}, {82, 79, 85, 0, 0, 0},
                {82, 85, 0, 0, 0, 0}, {82, 85, 65, 0, 0, 0},
                {82, 85, 65, 78, 0, 0}, {82, 85, 73, 0, 0, 0},
                {82, 85, 78, 0, 0, 0}, {82, 85, 79, 0, 0, 0},
                {83, 65, 0, 0, 0, 0}, {83, 65, 73, 0, 0, 0},
                {83, 65, 78, 0, 0, 0}, {83, 65, 78, 71, 0, 0},
                {83, 65, 79, 0, 0, 0}, {83, 69, 0, 0, 0, 0},
                {83, 69, 78, 0, 0, 0}, {83, 69, 78, 71, 0, 0},
                {83, 72, 65, 0, 0, 0}, {83, 72, 65, 73, 0, 0},
                {83, 72, 65, 78, 0, 0}, {83, 72, 65, 78, 71, 0},
                {83, 72, 65, 79, 0, 0}, {83, 72, 69, 0, 0, 0},
                {83, 72, 69, 78, 0, 0}, {88, 73, 78, 0, 0, 0},
                {83, 72, 69, 78, 0, 0}, {83, 72, 69, 78, 71, 0},
                {83, 72, 73, 0, 0, 0}, {83, 72, 79, 85, 0, 0},
                {83, 72, 85, 0, 0, 0}, {83, 72, 85, 65, 0, 0},
                {83, 72, 85, 65, 73, 0}, {83, 72, 85, 65, 78, 0},
                {83, 72, 85, 65, 78, 71}, {83, 72, 85, 73, 0, 0},
                {83, 72, 85, 78, 0, 0}, {83, 72, 85, 79, 0, 0},
                {83, 73, 0, 0, 0, 0}, {83, 79, 78, 71, 0, 0},
                {83, 79, 85, 0, 0, 0}, {83, 85, 0, 0, 0, 0},
                {83, 85, 65, 78, 0, 0}, {83, 85, 73, 0, 0, 0},
                {83, 85, 78, 0, 0, 0}, {83, 85, 79, 0, 0, 0},
                {84, 65, 0, 0, 0, 0}, {84, 65, 73, 0, 0, 0},
                {84, 65, 78, 0, 0, 0}, {84, 65, 78, 71, 0, 0},
                {84, 65, 79, 0, 0, 0}, {84, 69, 0, 0, 0, 0},
                {84, 69, 78, 71, 0, 0}, {84, 73, 0, 0, 0, 0},
                {84, 73, 65, 78, 0, 0}, {84, 73, 65, 79, 0, 0},
                {84, 73, 69, 0, 0, 0}, {84, 73, 78, 71, 0, 0},
                {84, 79, 78, 71, 0, 0}, {84, 79, 85, 0, 0, 0},
                {84, 85, 0, 0, 0, 0}, {84, 85, 65, 78, 0, 0},
                {84, 85, 73, 0, 0, 0}, {84, 85, 78, 0, 0, 0},
                {84, 85, 79, 0, 0, 0}, {87, 65, 0, 0, 0, 0},
                {87, 65, 73, 0, 0, 0}, {87, 65, 78, 0, 0, 0},
                {87, 65, 78, 71, 0, 0}, {87, 69, 73, 0, 0, 0},
                {87, 69, 78, 0, 0, 0}, {87, 69, 78, 71, 0, 0},
                {87, 79, 0, 0, 0, 0}, {87, 85, 0, 0, 0, 0},
                {88, 73, 0, 0, 0, 0}, {88, 73, 65, 0, 0, 0},
                {88, 73, 65, 78, 0, 0}, {88, 73, 65, 78, 71, 0},
                {88, 73, 65, 79, 0, 0}, {88, 73, 69, 0, 0, 0},
                {88, 73, 78, 0, 0, 0}, {88, 73, 78, 71, 0, 0},
                {88, 73, 79, 78, 71, 0}, {88, 73, 85, 0, 0, 0},
                {88, 85, 0, 0, 0, 0}, {88, 85, 65, 78, 0, 0},
                {88, 85, 69, 0, 0, 0}, {88, 85, 78, 0, 0, 0},
                {89, 65, 0, 0, 0, 0}, {89, 65, 78, 0, 0, 0},
                {89, 65, 78, 71, 0, 0}, {89, 65, 79, 0, 0, 0},
                {89, 69, 0, 0, 0, 0}, {89, 73, 0, 0, 0, 0},
                {89, 73, 78, 0, 0, 0}, {89, 73, 78, 71, 0, 0},
                {89, 79, 0, 0, 0, 0}, {89, 79, 78, 71, 0, 0},
                {89, 79, 85, 0, 0, 0}, {89, 85, 0, 0, 0, 0},
                {89, 85, 65, 78, 0, 0}, {89, 85, 69, 0, 0, 0},
                {89, 85, 78, 0, 0, 0}, {74, 85, 78, 0, 0, 0},
                {89, 85, 78, 0, 0, 0}, {90, 65, 0, 0, 0, 0},
                {90, 65, 73, 0, 0, 0}, {90, 65, 78, 0, 0, 0},
                {90, 65, 78, 71, 0, 0}, {90, 65, 79, 0, 0, 0},
                {90, 69, 0, 0, 0, 0}, {90, 69, 73, 0, 0, 0},
                {90, 69, 78, 0, 0, 0}, {90, 69, 78, 71, 0, 0},
                {90, 72, 65, 0, 0, 0}, {90, 72, 65, 73, 0, 0},
                {90, 72, 65, 78, 0, 0}, {90, 72, 65, 78, 71, 0},
                {67, 72, 65, 78, 71, 0}, {90, 72, 65, 78, 71, 0},
                {90, 72, 65, 79, 0, 0}, {90, 72, 69, 0, 0, 0},
                {90, 72, 69, 78, 0, 0}, {90, 72, 69, 78, 71, 0},
                {90, 72, 73, 0, 0, 0}, {83, 72, 73, 0, 0, 0},
                {90, 72, 73, 0, 0, 0}, {90, 72, 79, 78, 71, 0},
                {90, 72, 79, 85, 0, 0}, {90, 72, 85, 0, 0, 0},
                {90, 72, 85, 65, 0, 0}, {90, 72, 85, 65, 73, 0},
                {90, 72, 85, 65, 78, 0}, {90, 72, 85, 65, 78, 71},
                {90, 72, 85, 73, 0, 0}, {90, 72, 85, 78, 0, 0},
                {90, 72, 85, 79, 0, 0}, {90, 73, 0, 0, 0, 0},
                {90, 79, 78, 71, 0, 0}, {90, 79, 85, 0, 0, 0},
                {90, 85, 0, 0, 0, 0}, {90, 85, 65, 78, 0, 0},
                {90, 85, 73, 0, 0, 0}, {90, 85, 78, 0, 0, 0},
                {90, 85, 79, 0, 0, 0}, {0, 0, 0, 0, 0, 0},
                {83, 72, 65, 78, 0, 0}, {0, 0, 0, 0, 0, 0},};

        private static final String FIRST_PINYIN_UNIHAN = "\u963F";
        private static final String LAST_PINYIN_UNIHAN = "\u9FFF";

        private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);

        private static final HanziToPinyin sInstance = new HanziToPinyin();

        public static class Token {

            public static final int LATIN = 1;
            public static final int PINYIN = 2;
            public static final int UNKNOWN = 3;

            public Token() { }


            public int type;

            public String source;

            public String target;
        }

        protected HanziToPinyin() { }

        public static HanziToPinyin getInstance() {
            return sInstance;
        }

        private Token getToken(char character) {
            Token token = new Token();
            final String letter = Character.toString(character);
            token.source = letter;
            int offset = -1;
            int cmp;
            if (character < 256) {
                token.type = Token.LATIN;
                token.target = letter;
                return token;
            } else {
                cmp = COLLATOR.compare(letter, FIRST_PINYIN_UNIHAN);
                if (cmp < 0) {
                    token.type = Token.UNKNOWN;
                    token.target = letter;
                    return token;
                } else if (cmp == 0) {
                    token.type = Token.PINYIN;
                    offset = 0;
                } else {
                    cmp = COLLATOR.compare(letter, LAST_PINYIN_UNIHAN);
                    if (cmp > 0) {
                        token.type = Token.UNKNOWN;
                        token.target = letter;
                        return token;
                    } else if (cmp == 0) {
                        token.type = Token.PINYIN;
                        offset = UNIHANS.length - 1;
                    }
                }
            }

            token.type = Token.PINYIN;
            if (offset < 0) {
                int begin = 0;
                int end = UNIHANS.length - 1;
                while (begin <= end) {
                    offset = (begin + end) / 2;
                    final String unihan = Character.toString(UNIHANS[offset]);
                    cmp = COLLATOR.compare(letter, unihan);
                    if (cmp == 0) {
                        break;
                    } else if (cmp > 0) {
                        begin = offset + 1;
                    } else {
                        end = offset - 1;
                    }
                }
            }
            if (cmp < 0) {
                offset--;
            }
            StringBuilder pinyin = new StringBuilder();
            for (int j = 0; j < PINYINS[offset].length && PINYINS[offset][j] != 0; j++) {
                pinyin.append((char) PINYINS[offset][j]);
            }
            token.target = pinyin.toString();
            if (TextUtils.isEmpty(token.target)) {
                token.type = Token.UNKNOWN;
                token.target = token.source;
            }
            return token;
        }
    }

    private static class Cn2Spell {
        private static final int[] pyvalue = new int[]{-20319, -20317, -20304, -20295, -20292, -20283, -20265, -20257, -20242, -20230, -20051, -20036, -20032,
                -20026, -20002, -19990, -19986, -19982, -19976, -19805, -19784, -19775, -19774, -19763, -19756, -19751, -19746, -19741, -19739, -19728,
                -19725, -19715, -19540, -19531, -19525, -19515, -19500, -19484, -19479, -19467, -19289, -19288, -19281, -19275, -19270, -19263, -19261,
                -19249, -19243, -19242, -19238, -19235, -19227, -19224, -19218, -19212, -19038, -19023, -19018, -19006, -19003, -18996, -18977, -18961,
                -18952, -18783, -18774, -18773, -18763, -18756, -18741, -18735, -18731, -18722, -18710, -18697, -18696, -18526, -18518, -18501, -18490,
                -18478, -18463, -18448, -18447, -18446, -18239, -18237, -18231, -18220, -18211, -18201, -18184, -18183, -18181, -18012, -17997, -17988,
                -17970, -17964, -17961, -17950, -17947, -17931, -17928, -17922, -17759, -17752, -17733, -17730, -17721, -17703, -17701, -17697, -17692,
                -17683, -17676, -17496, -17487, -17482, -17468, -17454, -17433, -17427, -17417, -17202, -17185, -16983, -16970, -16942, -16915, -16733,
                -16708, -16706, -16689, -16664, -16657, -16647, -16474, -16470, -16465, -16459, -16452, -16448, -16433, -16429, -16427, -16423, -16419,
                -16412, -16407, -16403, -16401, -16393, -16220, -16216, -16212, -16205, -16202, -16187, -16180, -16171, -16169, -16158, -16155, -15959,
                -15958, -15944, -15933, -15920, -15915, -15903, -15889, -15878, -15707, -15701, -15681, -15667, -15661, -15659, -15652, -15640, -15631,
                -15625, -15454, -15448, -15436, -15435, -15419, -15416, -15408, -15394, -15385, -15377, -15375, -15369, -15363, -15362, -15183, -15180,
                -15165, -15158, -15153, -15150, -15149, -15144, -15143, -15141, -15140, -15139, -15128, -15121, -15119, -15117, -15110, -15109, -14941,
                -14937, -14933, -14930, -14929, -14928, -14926, -14922, -14921, -14914, -14908, -14902, -14894, -14889, -14882, -14873, -14871, -14857,
                -14678, -14674, -14670, -14668, -14663, -14654, -14645, -14630, -14594, -14429, -14407, -14399, -14384, -14379, -14368, -14355, -14353,
                -14345, -14170, -14159, -14151, -14149, -14145, -14140, -14137, -14135, -14125, -14123, -14122, -14112, -14109, -14099, -14097, -14094,
                -14092, -14090, -14087, -14083, -13917, -13914, -13910, -13907, -13906, -13905, -13896, -13894, -13878, -13870, -13859, -13847, -13831,
                -13658, -13611, -13601, -13406, -13404, -13400, -13398, -13395, -13391, -13387, -13383, -13367, -13359, -13356, -13343, -13340, -13329,
                -13326, -13318, -13147, -13138, -13120, -13107, -13096, -13095, -13091, -13076, -13068, -13063, -13060, -12888, -12875, -12871, -12860,
                -12858, -12852, -12849, -12838, -12831, -12829, -12812, -12802, -12607, -12597, -12594, -12585, -12556, -12359, -12346, -12320, -12300,
                -12120, -12099, -12089, -12074, -12067, -12058, -12039, -11867, -11861, -11847, -11831, -11798, -11781, -11604, -11589, -11536, -11358,
                -11340, -11339, -11324, -11303, -11097, -11077, -11067, -11055, -11052, -11045, -11041, -11038, -11024, -11020, -11019, -11018, -11014,
                -10838, -10832, -10815, -10800, -10790, -10780, -10764, -10587, -10544, -10533, -10519, -10331, -10329, -10328, -10322, -10315, -10309,
                -10307, -10296, -10281, -10274, -10270, -10262, -10260, -10256, -10254};
        private static final String[] pystr = new String[] {"A","AI","AN","ANG","AO","BA","BAI","BAN","BANG","BAO","BEI","BEN","BENG","BI","BIAN","BIAO","BIE","BIN",
                "BING","BO","BU","CA","CAI","CAN","CANG","CAO","CE","CENG","CHA","CHAI","CHAN","CHANG","CHAO","CHE","CHEN","CHENG","CHI","CHONG","CHOU","CHU",
                "CHUAI","CHUAN","CHUANG","CHUI","CHUN","CHUO","CI","CONG","COU","CU","CUAN","CUI","CUN","CUO","DA","DAI","DAN","DANG","DAO","DE","DENG","DI",
                "DIAN","DIAO","DIE","DING","DIU","DONG","DOU","DU","DUAN","DUI","DUN","DUO","E","EN","ER","FA","FAN","FANG","FEI","FEN","FENG","FO","FOU","FU",
                "GA","GAI","GAN","GANG","GAO","GE","GEI","GEN","GENG","GONG","GOU","GU","GUA","GUAI","GUAN","GUANG","GUI","GUN","GUO","HA","HAI","HAN","HANG",
                "HAO","HE","HEI","HEN","HENG","HONG","HOU","HU","HUA","HUAI","HUAN","HUANG","HUI","HUN","HUO","JI","JIA","JIAN","JIANG","JIAO","JIE","JIN",
                "JING","JIONG","JIU","JU","JUAN","JUE","JUN","KA","KAI","KAN","KANG","KAO","KE","KEN","KENG","KONG","KOU","KU","KUA","KUAI","KUAN","KUANG",
                "KUI","KUN","KUO","LA","LAI","LAN","LANG","LAO","LE","LEI","LENG","LI","LIA","LIAN","LIANG","LIAO","LIE","LIN","LING","LIU","LONG","LOU","LU",
                "LV","LUAN","LUE","LUN","LUO","MA","MAI","MAN","MANG","MAO","ME","MEI","MEN","MENG","MI","MIAN","MIAO","MIE","MIN","MING","MIU","MO","MOU",
                "MU","NA","NAI","NAN","NANG","NAO","NE","NEI","NEN","NENG","NI","NIAN","NIANG","NIAO","NIE","NIN","NING","NIU","NONG","NU","NV","NUAN","NUE",
                "NUO","O","OU","PA","PAI","PAN","PANG","PAO","PEI","PEN","PENG","PI","PIAN","PIAO","PIE","PIN","PING","PO","PU","QI","QIA","QIAN","QIANG",
                "QIAO","QIE","QIN","QING","QIONG","QIU","QU","QUAN","QUE","QUN","RAN","RANG","RAO","RE","REN","RENG","RI","RONG","ROU","RU","RUAN","RUI","RUN",
                "RUO","SA","SAI","SAN","SANG","SAO","SE","SEN","SENG","SHA","SHAI","SHAN","SHANG","SHAO","SHE","SHEN","SHENG","SHI","SHOU","SHU","SHUA","SHUAI",
                "SHUAN","SHUANG","SHUI","SHUN","SHUO","SI","SONG","SOU","SU","SUAN","SUI","SUN","SUO","TA","TAI","TAN","TANG","TAO","TE","TENG","TI","TIAN",
                "TIAO","TIE","TING","TONG","TOU","TU","TUAN","TUI","TUN","TUO","WA","WAI","WAN","WANG","WEI","WEN","WENG","WO","WU","XI","XIA","XIAN","XIANG",
                "XIAO","XIE","XIN","XING","XIONG","XIU","XU","XUAN","XUE","XUN","YA","YAN","YANG","YAO","YE","YI","YIN","YING","YO","YONG","YOU","YU","YUAN",
                "YUE","YUN","ZA","ZAI","ZAN","ZANG","ZAO","ZE","ZEI","ZEN","ZENG","ZHA","ZHAI","ZHAN","ZHANG","ZHAO","ZHE","ZHEN","ZHENG","ZHI","ZHONG","ZHOU",
                "ZHU","ZHUA","ZHUAI","ZHUAN","ZHUANG","ZHUI","ZHUN","ZHUO","ZI","ZONG","ZOU","ZU","ZUAN","ZUI","ZUN","ZUO",
        };
        private static final Cn2Spell cn2Spell = new Cn2Spell();

        public static Cn2Spell getInstance() {
            return cn2Spell;
        }

        // 汉字转成ASCII码
        private int getChsAscii(String chs) {
            int asc = 0;
            try {
                byte[] bytes = chs.getBytes("gbk");
                if (bytes == null || bytes.length > 2 || bytes.length <= 0) {
                    throw new RuntimeException("illegal resource string");
                }
                if (bytes.length == 1) {
                    asc = bytes[0];
                }
                if (bytes.length == 2) {
                    int highByte = 256 + bytes[0];
                    int lowByte = 256 + bytes[1];
                    asc = (256 * highByte + lowByte) - 256 * 256;
                }
            } catch (Exception ignored) { }
            return asc;
        }

        public String convert(String str) {
            int ascii = getChsAscii(str);
            if (ascii > 0 && ascii < 160) {
                return String.valueOf((char) ascii);
            } else {
                int left = 0;
                int right = pyvalue.length - 1;
                int mid;
                while (left <= right) {
                    mid = (left + right) / 2;
                    if (pyvalue[mid] > ascii) {
                        right = mid - 1;
                    } else {
                        left = mid + 1;
                    }
                }
                return right < 0 ? null : pystr[right];
            }
        }
    }

    private static final HanziToPinyin hanziToPinyin = HanziToPinyin.getInstance();
    private static final Cn2Spell cn2Spell = Cn2Spell.getInstance();

    private static final HashMap<Character, String> charMap = new HashMap<>();

    public static String getPinyin(String src) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c <= (char) 255) {
                if (c >= 'a' && c <= 'z') {
                    stringBuilder.append((char) (c - 32));
                } else {
                    stringBuilder.append(c);
                }
            } else {
                String result = charMap.get(c);
                if (result != null) {
                    stringBuilder.append(result);
                    continue;
                }
                result = cn2Spell.convert(Character.toString(c));
                if (result == null || "ZUO".equals(result)) {
                    result = hanziToPinyin.getToken(c).target;
                }
                stringBuilder.append(result);
                charMap.put(c, result);
            }
        }
        return stringBuilder.toString();
    }

    public static char getPinyinChar(char c) {
        char temp;
        if (c <= (char) 255) {
            if (c >= 'a' && c <= 'z') {
                temp = (char) (c - 32);
            } else {
                temp = c;
            }
        } else {
            String result = charMap.get(c);
            if (result != null) {
                temp = result.charAt(0);
            } else {
                result = cn2Spell.convert(Character.toString(c));
                if (result == null || "ZUO".equals(result)) {
                    result = hanziToPinyin.getToken(c).target;
                }
                charMap.put(c, result);
                temp = result.charAt(0);
            }
        }
        if ((temp >= 'A' && temp <= 'Z') || (temp >= '0' && temp <= '9')) {
            return temp;
        }
        return '#';
    }
}
