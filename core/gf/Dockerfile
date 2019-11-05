FROM haskell:latest
RUN mkdir -p /opt/gf
WORKDIR /opt/gf

RUN apt-get update
RUN apt-get install wget python python-pip -y


RUN pip install backports.tempfile

RUN wget https://www.grammaticalframework.org/download/gf_3.10-2_amd64.deb
RUN dpkg -i gf_3.10-2_amd64.deb && rm -rf gf_3.10-2_amd64.deb

ADD core/gf/server.py server.py

CMD ["/usr/bin/python", "server.py"]