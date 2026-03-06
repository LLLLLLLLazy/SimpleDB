# SimpleDB 常用命令（中文）

> 在项目根目录执行：`/Users/lazy/SimpleDB/simple-db-hw-2021/simple-db-hw-2021`

## 1. 环境检查

```bash
java -version
javac -version
ant -version
```

## 2. 构建相关

```bash
ant clean      # 清理 bin/dist/javadoc/testreport
ant compile    # 编译源码
ant dist       # 生成 dist/simpledb.jar
ant -projecthelp  # 查看所有可用目标
```

## 3. 测试相关（最常用）

```bash
ant test                           # 运行所有单元测试（test/simpledb）
ant systemtest                     # 运行所有系统测试（test/simpledb/systemtest）
ant runtest -Dtest=TupleTest       # 运行单个单元测试
ant runsystest -Dtest=ScanTest     # 运行单个系统测试
ant test-and-handin                # 测试全部通过后再打包提交
```

## 4. 详细测试命令（文档标准用法 + 常用扩展）

### 4.1 文档标准用法（lab1.md）

```bash
ant test                        # 跑全部 unit tests（lab1.md:72）
ant runtest -Dtest=TupleTest    # 跑单个 unit test（lab1.md:79）
ant systemtest                  # 跑全部 system tests（lab1.md:123）
ant runsystest -Dtest=TestName  # 跑单个 system test（lab1.md:111）
```

说明：
- `-Dtest=...` 只写类名，不写包名、不写 `.java` 后缀。
- unit test 类名来自 `test/simpledb/*.java`。
- system test 类名来自 `test/simpledb/systemtest/*.java`。

### 4.2 单个测试示例

```bash
# 单元测试示例
ant runtest -Dtest=CatalogTest
ant runtest -Dtest=TupleDescTest
ant runtest -Dtest=JoinTest

# 系统测试示例
ant runsystest -Dtest=ScanTest
ant runsystest -Dtest=FilterTest
ant runsystest -Dtest=LogTest
```

### 4.3 提交前建议顺序

```bash
ant clean
ant test
ant systemtest
```

### 4.4 诊断与报告

```bash
ant testcompile     # 只编译源码和测试，不执行测试
ant test-report     # 生成 HTML 报告到 testreport/
```

`test-report` 常用于一次性查看全部失败项。

## 5. 推荐日常流程

```bash
ant clean
ant test
ant systemtest
```

## 6. 运行主程序（SimpleDb）

`simpledb.SimpleDb` 需要子命令参数，常见示例：

```bash
ant dist
java -classpath dist/simpledb.jar simpledb.SimpleDb convert some.txt 3
java -classpath dist/simpledb.jar simpledb.SimpleDb print some.dat 3
```

## 7. 提交打包

```bash
ant handin
```

会生成 `lab-handin.tar.bz2`。
