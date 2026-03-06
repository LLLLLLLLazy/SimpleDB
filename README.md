# 课程信息 - 西北工业大学 系统综合实验

本项目用于西北工业大学的“系统综合实验”课程。项目基于 MIT 6.830/6.814 的 SimpleDB 实验框架构建。

我们将使用 Git（一种源代码版本控制工具）来分发实验代码。这将允许你方便地增量下载代码，也方便我们推送任何必要的修复更新。

你还可以使用 Git 来提交和备份你在实验过程中的代码进度。课程的 Git 仓库托管在 GitHub 上。GitHub 是一个为成千上万开源项目运行 Git 服务器的网站。在我们的课程中，你的代码将放在一个只有你和课程教师可见的私有仓库中。

本文档介绍了你需要进行的 Git 入门常识，以及如何通过 GitHub 下载和上传实验代码。

**如果你不是相关课程的注册学生，欢迎你跟随学习，但我们请求你务必对你的解答保持私有（PRIVATE），不要公开。**

## 目录

- [学习 Git](#learning-git)
- [配置 GitHub](#setting-up-github)
- [安装 Git](#installing-git)
- [配置 Git](#setting-up-git)
- [获取新发布的实验](#getting-newly-released-lab)
- [注意事项](#word-of-caution)
- [帮助！](#help)

## 学习 Git <a id="learning-git"></a>

关于如何使用 Git，现在有非常丰富的指南。它们从互动式教程到纯文本描述不一而足。找到一个适合你的并试一试；犯错并解决它们是极好的学习方法。以下是 GitHub 推荐的资源链接：
[https://help.github.com/articles/what-are-other-good-resources-for-learning-git-and-github][resources]。

如果你之前完全没有接触过 Git，你可能会发现这个基于网页的教程很有帮助：[Try Git](https://try.github.io/levels/1/challenges/1)。

## <a name="setting-up-github"></a> 配置 GitHub

现在你已经对 Git 有了基本了解，是时候开始使用 GitHub 了。

0. 安装 Git。（见下文建议）。
1. 如果你还没有 GitHub 账号，请在这里注册一个：[https://github.com/join][join]。

### 安装 Git <a name="installing-git"></a>

以下这些指令主要针对 bash/linux 环境。在 Linux 下安装 Git 通常只需要简单的执行 `apt-get / yum / 等 install` 即可。

如果要在 Linux、OSX 或 Windows 下安装 Git，详细指南可参考：
[GitBook: 安装](http://git-scm.com/book/zh/v2/起步-安装-Git)。

如果你使用的是 Eclipse/IntelliJ 等 IDE，许多版本已经内置并配置好了 Git。具体操作可能会与下面的命令行说明略有不同，但能适用于任何操作系统。详细的图文说明可以在以下链接查看：
[EGit 用户指南](http://wiki.eclipse.org/EGit/User_Guide)、[EGit 教程](http://eclipsesource.com/blogs/tutorials/egit-tutorial) 或 
[IntelliJ 帮助](https://www.jetbrains.com/help/idea/version-control-integration.html)。

## 配置 Git <a name="setting-up-git"></a>

确保你已经通过上一节内容安装了 Git。

1. 我们要做的第一件事是在命令行中输入以下命令来克隆当前的实验仓库：

   ```bash
    $ git clone https://github.com/MIT-DB-Class/simple-db-hw-2021.git
   ```

   现在，每次发布新实验或有补丁更新时，你可以运行：

   ```bash
    $ git pull
   ```
   来获取最新内容。 
   
   就是这样。你可以开始做实验了！尽管如此，我们强烈建议你不仅仅使用 Git 来简单地下载实验。在本指南的其余部分，我们将指导你如何在自己的开发过程中使用 Git 进行版本控制。

2. 请注意，你是从课设官方仓库进行克隆的，这意味着将你的代码直接推送到官方仓库中是**不合适**的。如果你想使用 Git 记录并留存你的解答版本，你需要创建自己的仓库来记录更改。
   你可以在 GitHub 左侧点击“New”（新建）来实现，创建时请务必选择 **Private**（私有仓库），这样别人就无法看到你的代码！现在我们要将刚才克隆下来的仓库修改为指向你的个人仓库。

3. 默认情况下，名为 `origin` 的远程仓库指向的是你克隆源码的位置。你应该会看到以下输出：

   ```bash
    $ git remote -v
        origin https://github.com/MIT-DB-Class/simple-db-hw-2021.git (fetch)
        origin https://github.com/MIT-DB-Class/simple-db-hw-2021.git (push)
   ```

   我们不希望原仓库仍作为 origin。相反，我们要把它指向你自己的私人仓库。为此，请输入以下命令重命名记录：

   ```bash
    $ git remote rename origin upstream
   ```

   现在你应该会看到：

   ```bash
    $ git remote -v
        upstream https://github.com/MIT-DB-Class/simple-db-hw-2021.git (fetch)
        upstream https://github.com/MIT-DB-Class/simple-db-hw-2021.git (push)
   ```

4. 最后，你需要给你的新仓库指定一个新的 `origin` 数据源。输入以下命令，将 URL 替换为你在 GitHub 上新建仓库的链接：

   ```bash
    $ git remote add origin https://github.com/[your-repo]
   ```

   如果遇到类似以下的错误：
   ```
   Could not rename config section 'remote.[old name]' to 'remote.[new name]'
   ```
   或者该错误：
   ```
   fatal: remote origin already exists.
   ```
   这通常与你使用的 Git 版本有关。要修复并覆盖原有的 origin，只需执行以下命令：

   ```bash
   $ git remote set-url origin https://github.com/[your-repo]
   ```

   作为参考，如果设置全都正确无误，你最终的 `git remote -v` 查看到的列表应该如下所示：

   ```bash
    $ git remote -v
        upstream https://github.com/MIT-DB-Class/simple-db-hw-2021.git (fetch)
        upstream https://github.com/MIT-DB-Class/simple-db-hw-2021.git (push)
        origin https://github.com/[your-repo] (fetch)
        origin https://github.com/[your-repo] (push)
   ```

5. 让我们进行一次测试，把你的主分支推送到自己的 GitHub 私有仓库中。在命令行输入：

   ```bash
    $ git push -u origin main
   ```
   *(注：老项目的默认主分支可能为 master，现如今多数项目默认主分支为 main，请根据你的本地实际情况使用 `git push -u origin main` 或是 `git push -u origin master`)*

   随后你大致会看到如下输出信息：

   ```
	Counting objects: 59, done.
	Delta compression using up to 4 threads.
	Compressing objects: 100% (53/53), done.
	Writing objects: 100% (59/59), 420.46 KiB | 0 bytes/s, done.
	Total 59 (delta 2), reused 59 (delta 2)
	remote: Resolving deltas: 100% (2/2), done.
	To https://github.com/[your-repo].git
	 * [new branch]      main -> main
	Branch main set up to track remote branch main from origin.
   ```

6. 上面的带有 `-u` 参数的命令比较特殊，只有在第一次设置远程跟踪分支时才需要附加。在日后的操作里，你应该可以直接运行不带参数的 `git push` 。试一试，你应该会看到以下结果：

   ```bash
    $ git push
      Everything up-to-date
   ```

如果你不太了解 Git，这些操作可能让你觉得有些晦涩。只要继续使用它，你会慢慢习惯并完全掌握它。在实验开发过程中，我们不强制要求你不断地去使用 commit 和 push 命令，但通过良好的版本管理确实会对代码查错和记录大有裨益。

## 获取新发布的实验 <a id="getting-newly-released-lab"></a>

(直到有新实验内容下发前，你暂时用不到这部分指南。)

只要你按照上一节的说明正确配置好了仓库，并保留了 `upstream` 记录，拉取新实验框架或是历史解答是非常容易的事情。

1. 所有新实验都会发布到相关的 `upstream` 源仓库中。

2. 一旦新实验发布通告，请在自己的 simpledb 目录中提取官方最近更改：

   ```bash
    $ git pull upstream main
   ```

   **或者** 如果你想对整个合并过程控制得更明确一点，可以先提取（fetch）然后再合并（merge）：

   ```bash
    $ git fetch upstream
    $ git merge upstream/main
   ```

   整合无误后，再提交同步到你自己的个人远程分支：
   ```bash
    $ git push origin main
   ```

3. 如果你每一讲实验都遵循了正确步骤，通常不会遇到合并冲突，一切应该都能水到渠成。

## 注意事项 <a id="word-of-caution"></a>

Git 是一个分布式版本控制系统。这意味着所有的提交和归存操作均在脱机状态下运行，改变全在本地生效，直到你亲自去执行 `git pull` 或 `git push` 和远程端点沟通。这种高度离线的特性是非常棒的。

坏处是你绝对有可能会忘记主动 `git push` 推送自己的最新改动。这就是为什么我们**强烈**建议你定期在实验之后登录 GitHub 网页端去确认一下记录，确保云端上的代码状态正如你预期保留一样，以免造成代码进度丢失。

## 帮助！ <a id="help"></a>

如果在配置过程中你需要任何支持和帮助，欢迎随时联系助教老师或授课指导教师。

[join]: https://github.com/join
[resources]: https://help.github.com/articles/what-are-other-good-resources-for-learning-git-and-github
[ssh-key]: https://help.github.com/articles/generating-ssh-keys


# SimpleDB 常用命令速查手册

> **注意**：本手册中所有命令均需在项目根目录（包含 `build.xml` 的目录）下执行：
> `/Users/lazy/SimpleDB/simple-db-hw-2021/simple-db-hw-2021`

## 1. 环境检查

在开始实验之前，建议先检查 Java 和 Ant 的环境版本是否配置正确：

```bash
java -version   # 检查 Java 运行时版本
javac -version  # 检查 Java 编译器版本
ant -version    # 检查 Apache Ant 构建工具版本
```

## 2. 构建与清理

SimpleDB 使用 Apache Ant 进行项目管理。以下是常用的构建命令：

```bash
ant clean         # 清理构建生成的临时文件（包括 bin/, dist/, javadoc/, testreport/ 等目录）
ant compile       # 仅编译项目核心源码
ant dist          # 编译源码并打包生成可执行的 dist/simpledb.jar
ant -projecthelp  # 查看项目中所有可通过 ant 执行的可用目标（Targets）
```

## 3. 测试相关（核心常用）

在开发 Lab 的过程中，你会频繁地使用测试命令来验证代码逻辑。

### 3.1 运行全部测试

```bash
ant test            # 运行所有的单元测试（对应 test/simpledb/ 目录下的基础测试）
ant systemtest      # 运行所有的系统测试（对应 test/simpledb/systemtest/ 目录下的集成测试）
```

### 3.2 运行单个测试

当你在修复特定的 Bug 或只关注某个特定模块时，指定类的测试会大大节省时间：

```bash
ant runtest -Dtest=TestName      # 运行单个单元测试（基础测试）
ant runsystest -Dtest=TestName   # 运行单个系统测试
```

**⚠️ 参数说明与编写规范：**
* `-Dtest=...` 后面**只写类名本身**，不要包含包名路径，也**不要**加 `.java` 后缀。
* `runtest` 对应的类必须存在于 `test/simpledb/` 目录下。
* `runsystest` 对应的类必须存在于 `test/simpledb/systemtest/` 目录下。

### 3.3 常用单个测试示例

你可以直接复制以下常用命令去验证指定模块：

```bash
# 常见单元测试（Unit Tests）
ant runtest -Dtest=TupleTest
ant runtest -Dtest=TupleDescTest
ant runtest -Dtest=CatalogTest
ant runtest -Dtest=JoinTest

# 常见系统测试（System Tests）
ant runsystest -Dtest=ScanTest
ant runsystest -Dtest=FilterTest
ant runsystest -Dtest=LogTest
```

### 3.4 生成诊断与测试报告

如果你想快速查看全部的错误详情，而不仅仅依赖终端输出，生成网页版报告会很方便。

```bash
ant testcompile     # 仅编译源码和测试文件，不执行实际测试
ant test-report     # 运行测试并生成详细的 HTML 格式测试报告，输出在 testreport/ 目录下
```

## 4. 推荐的开发工作流

为了避免因旧缓存导致的玄学报错，建议养成规范的测试习惯：

### 4.1 日常开发验证流

每次完成一个功能模块时，确保单元和系统测试均通过：

```bash
ant clean && ant test && ant systemtest
```

### 4.2 最终打包提交流

当你完成了当前 Lab 的所有要求，准备提交作业时：

```bash
ant test-and-handin   # （强烈推荐）运行所有测试，全部通过后再自动打包提交文件
# 或者手动分步执行：
ant clean
ant test
ant systemtest
ant handin            # 最终会生成名为 `lab-handin.tar.bz2` 的压缩包文件供你提交
```

## 5. 运行主程序（CLI 操作）

如果你需要在命令行界面直接使用 SimpleDB 处理数据文件，可以通过打包后的 JAR 文件运行 `simpledb.SimpleDb` 主类：

```bash
# 第一步：确保已经打包生成了 dist/simpledb.jar
ant dist

# 示例 1：将普通文本数据文件转换成 SimpleDB 专用的二进制 .dat 格式文件
java -classpath dist/simpledb.jar simpledb.SimpleDb convert some.txt 3

# 示例 2：打印已有的 SimpleDB 数据文件内容
java -classpath dist/simpledb.jar simpledb.SimpleDb print some.dat 3
```
