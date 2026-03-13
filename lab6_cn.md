# 6.830 Lab 6：回滚与恢复

**发布时间：2021 年 5 月 3 日，周一**<br>
**截止时间：2021 年 5 月 19 日，周三，EST 晚上 11:59**

## 0. 简介

在本实验中，你将实现基于日志的 abort 回滚，以及基于日志的崩溃恢复。我们已经提供了定义日志格式并在事务执行过程中适当时机向日志文件追加记录的代码。你需要基于日志文件的内容实现回滚和恢复。

我们提供的日志代码生成的是面向“整页物理 UNDO / REDO”的日志记录。当页面第一次被读入时，我们的代码会把该页面的原始内容保存为 before-image。当事务更新一个页面时，对应的日志记录会同时包含该页面最初保存的 before-image，以及修改后的 after-image。你将在 abort 回滚时用 before-image 撤销修改，在恢复过程中用它撤销 loser transaction 的更新；而 after-image 则用于在恢复时重做 winner transaction 的更新。

我们之所以能使用整页物理 UNDO（而 ARIES 必须使用逻辑 UNDO），是因为我们采用的是页面级加锁，并且系统中没有索引结构会在 UNDO 时与当初写日志时不一致。页面级加锁简化实现的原因在于：如果某事务修改了一个页面，它就必须持有该页的排他锁，这意味着没有其他事务会并发修改该页，因此我们只需把整个页面内容覆盖回去即可完成 UNDO。

你的 BufferPool 当前通过“丢弃 dirty page”来实现 abort，并通过“只在 commit 时把 dirty page 强制刷盘”来假装实现原子提交。引入日志之后，buffer 管理可以更灵活（支持 STEAL 和 NO-FORCE），而我们的测试代码也会在某些时刻主动调用 `BufferPool.flushAllPages()`，以验证这种灵活性。

## 1. 开始

你应当从自己提交的 Lab 5 代码开始（如果你没有提交 Lab 5，或者你的解答不能正常工作，请联系助教讨论可选方案）。

你需要修改一些已有源码并新增几个文件。请按以下步骤操作：

* 首先进入你的项目目录（通常叫做 `simple-db-hw`），并从主 GitHub 仓库拉取更新：

  ```
  $ cd simple-db-hw
  $ git pull upstream master
  ```
* 然后对现有代码做以下修改：
    1. 在 `BufferPool.flushPage()` 中、你调用 `writePage(p)` 之前插入如下代码，其中 `p` 是即将写出的页面对象：
    ```
        // append an update record to the log, with 
        // a before-image and after-image.
        TransactionId dirtier = p.isDirty();
        if (dirtier != null){
          Database.getLogFile().logWrite(dirtier, p.getBeforeImage(), p);
          Database.getLogFile().force();
        }
    ```
  这会让日志系统把一次更新写入日志。
  我们强制刷日志，是为了保证日志记录先落盘，再把页面写回磁盘。


2. 你当前的 `BufferPool.transactionComplete()` 会对已提交事务修改过的每一页调用 `flushPage()`。对于每个这样的页面，在完成 flush 后再调用一次 `p.setBeforeImage()`：
   ```
   // use current page contents as the before-image
   // for the next transaction that modifies this page.
   p.setBeforeImage();
   ```
   一次更新被提交后，页面的 before-image 需要更新为这个已提交版本，以便后续事务在 abort 时能够回滚到该提交版本。
   （注意：我们不能直接在 `flushPage()` 中调用 `setBeforeImage()`，因为 `flushPage()` 可能在事务**并未提交**时也被调用。我们的测试确实会这么做。如果你把 `transactionComplete()` 实现成调用 `flushPages()`，那么你可能需要给 `flushPages()` 增加额外参数，以区分“提交导致的 flush”和“普通 flush”。不过在这种情况下，我们更强烈建议你直接重写 `transactionComplete()`，改为使用 `flushPage()`。）

* 完成这些修改后，请做一次干净构建（命令行执行 `<tt>ant clean; ant
  </tt>`，或在 Eclipse 中使用项目菜单里的 “Clean”。）

* 此时，你的代码应该能通过 `LogTest` 系统测试中的前三个子测试，其余部分会失败：
  ```
  % ant runsystest -Dtest=LogTest
    ...
    [junit] Running simpledb.systemtest.LogTest
    [junit] Testsuite: simpledb.systemtest.LogTest
    [junit] Tests run: 10, Failures: 0, Errors: 7, Time elapsed: 0.42 sec
    [junit] Tests run: 10, Failures: 0, Errors: 7, Time elapsed: 0.42 sec
    [junit] 
    [junit] Testcase: PatchTest took 0.057 sec
    [junit] Testcase: TestFlushAll took 0.022 sec
    [junit] Testcase: TestCommitCrash took 0.018 sec
    [junit] Testcase: TestAbort took 0.03 sec
    [junit]     Caused an ERROR
    [junit] LogTest: tuple present but shouldn't be
    ...
  ```

* 如果你运行 <tt>ant runsystest -Dtest=LogTest</tt> 时看不到上面的输出，那么很可能是拉取新文件时出了问题，或者你做的修改与现有代码不兼容。继续之前请先定位并修复这个问题；必要时向我们求助。



## 2. 回滚

请阅读 `LogFile.java` 中的注释，以了解日志文件格式。
你会在 `LogFile.java` 中看到一组函数，例如 `logCommit()`，它们用于生成不同类型的日志记录并将其追加到日志文件中。


你的第一项任务是在 `LogFile.java` 中实现 `rollback()` 函数。该函数会在事务 abort 时被调用，而且调用发生在事务释放锁之前。它的职责是撤销该事务对数据库所做的全部修改。

你的 `rollback()` 应当读取日志文件，找出与该被中止事务相关的所有 update 记录，从每条记录中提取 before-image，并将 before-image 写回表文件。你需要用 `raf.seek()` 在日志文件中移动位置，并用 `raf.readInt()` 等方法读取内容。before-image 和 after-image 的读取可以使用 `readPageData()`。你可以使用 `tidToFirstLogRecord` 这个映射（它把事务 id 映射到日志文件中的第一个记录偏移量）来确定某事务应该从日志文件的哪个位置开始读。你还需要确保：凡是你把 before-image 写回表文件的页面，都必须从 buffer pool 中丢弃对应缓存页。

在开发过程中，你可能会发现 `Logfile.print()` 方法有助于查看当前日志内容。


***
**Exercise 1：`LogFile.rollback()`**


实现 `LogFile.rollback()`。


完成该练习后，你应当能够通过 `LogTest` 系统测试中的 `TestAbort` 和 `TestAbortCommitInterleaved` 两个子测试。

***


## 3. 恢复

如果数据库发生崩溃并随后重启，`LogFile.recover()` 会在任何新事务开始之前被调用。你的实现应当：



1. 读取最后一个 checkpoint（如果存在）。

2. 从 checkpoint（如果没有 checkpoint，则从日志开头）向前扫描，构建 loser transaction 集合。在这一轮扫描过程中执行 redo。你可以安全地从 checkpoint 开始 redo，因为 `LogFile.logCheckpoint()` 会把所有 dirty buffer 都刷到磁盘。

3. 撤销 loser transaction 的更新。



***
**Exercise 2：`LogFile.recover()`**



实现 `LogFile.recover()`。


完成该练习后，你应当能够通过 `LogTest` 系统测试的全部测试。


***

## 4. 提交流程

你必须提交代码（见下文），以及一份简短的 writeup（最多 1 页），说明你的实现思路。该 writeup 应包括：



*  描述你做出的设计决策，包括任何困难之处或意料之外的地方。

*  讨论并说明你在 `LogFile.java` 之外做出的任何修改。



### 4.1. 合作

本实验一个人即可完成，但如果你愿意，也可以和一位搭档合作。不允许更大的小组。请在你的 writeup 中清楚说明你与谁合作了（如果有）。

### 4.2. 提交作业

我们将使用 gradescope 对所有编程作业进行自动评分。你应该已经被邀请加入课程实例；如果没有，请联系我们，我们会帮助你完成设置。你可以在截止日期前多次提交，我们将以 gradescope 记录的最后一次提交为准。请在提交中附带名为 `lab3-writeup.txt` 的 writeup 文件。你还需要显式加入自己创建的其他文件，例如新的 `*.java` 文件。

向 gradescope 提交最简单的方式是上传包含代码的 `.zip` 文件。在 Linux/MacOS 上，你可以运行：

```bash
$ zip -r submission.zip src/ lab6-writeup.txt
```

<a name="bugs"></a>
### 4.3. 报告 bug

SimpleDB 是一套相对复杂的代码。你很可能会遇到 bug、不一致之处、糟糕/过时/错误的文档等问题。

因此，我们希望你以探索性的心态完成本实验。如果某些东西不清楚，甚至是错误的，不要生气；请尽量自己先搞清楚，或者给我们发一封友好的邮件。
请将（友善的）bug 报告发送到 <a
href="mailto:6.830-staff@mit.edu">6.830-staff@mit.edu</a>。
提交时请尽量包含：

* bug 的描述。

* 一个我们可以直接放入 `src/simpledb/test`
  目录中编译并运行的 <tt>.java</tt> 文件。

* 一个可复现该 bug 的 <tt>.txt</tt> 数据文件。我们应当能够使用 `PageEncoder` 将其转换为 <tt>.dat</tt> 文件。

如果你觉得自己遇到了 bug，也可以在 Piazza 的课程页面发帖。

<a name="grading"></a>
### 4.4 评分
<p>你总成绩的 75% 将取决于你的代码能否通过我们运行的系统测试套件。这些测试会是我们已提供测试的超集。在提交前，你应确保运行 <tt>ant test</tt> 和 <tt>ant systemtest</tt> 时都没有任何错误（即通过全部测试）。

**重要：** 在测试前，gradescope 会用我们自己的版本替换你的 <tt>build.xml</tt>、<tt>HeapFileEncoder.java</tt> 和整个 <tt>test</tt> 目录。这意味着你**不能**修改 <tt>.dat</tt> 文件格式。你在修改 API 时也要格外谨慎。你应当验证自己的代码能在未修改的测试上通过编译。

提交后，gradescope 会立刻给出失败测试（如果有）的反馈和错误输出。自动评分部分的成绩将由此决定。额外 25% 的成绩将基于你的 writeup 质量以及我们对你代码的主观评价。这部分也会在我们完成评分后发布到 gradescope 上。
